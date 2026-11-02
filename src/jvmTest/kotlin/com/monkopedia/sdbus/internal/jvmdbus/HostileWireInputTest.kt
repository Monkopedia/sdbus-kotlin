package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.Message
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.newsclub.net.unix.AFUNIXServerSocket
import org.newsclub.net.unix.AFUNIXSocketAddress

/**
 * Hostile-input coverage for the JVM wire backend (issue #190).
 *
 * Every case here is a frame (or a body fragment) that a peer can put on the wire but that no
 * conforming sender would produce: lengths above the D-Bus specification's maxima, lengths that go
 * NEGATIVE when narrowed to [Int], container nesting far past the spec's recursion limit, and
 * header fields carrying the wrong variant type. All of them must surface as
 * [DBusMarshallingException] — never as an [OutOfMemoryError], a [NegativeArraySizeException], a
 * [StackOverflowError], a [ClassCastException], and above all never as a *successful* decode of the
 * wrong value.
 *
 * The last test stands up a real fake AF_UNIX peer (SASL EXTERNAL, then one hostile frame, then it
 * holds the socket open) and pins the umbrella failure: a connection whose reader thread has died
 * must say so, rather than accepting calls that can only ever time out.
 */
class HostileWireInputTest {

    // --- framing-level lengths ------------------------------------------------------------------

    @Test
    fun frameDeclaringA2GbBody_isRejectedWithoutAttemptingTheAllocation() {
        val frame = framePrefix(bodyLength = 0x7F000000L, fieldsLength = 0L)

        assertFailsWith<DBusMarshallingException> {
            WireMessageCodec.read(ByteArrayInputStream(frame))
        }
    }

    @Test
    fun frameWithBodyLengthThatNarrowsNegative_isRejected() {
        val frame = framePrefix(bodyLength = 0x80000000L, fieldsLength = 0L)

        assertFailsWith<DBusMarshallingException> {
            WireMessageCodec.read(ByteArrayInputStream(frame))
        }
    }

    @Test
    fun frameWithHeaderFieldsLengthThatNarrowsNegative_isRejected() {
        val frame = framePrefix(bodyLength = 0L, fieldsLength = 0x80000000L)

        assertFailsWith<DBusMarshallingException> {
            WireMessageCodec.read(ByteArrayInputStream(frame))
        }
    }

    // --- body-level lengths ---------------------------------------------------------------------

    @Test
    fun stringLengthThatNarrowsNegative_isRejectedInsteadOfIndexingOutOfBounds() {
        for (declared in listOf(0x80000000L, 0xFFFFFFFFL)) {
            val bytes = littleEndianU32(declared) + byteArrayOf('A'.code.toByte(), 0)

            assertFailsWith<DBusMarshallingException>("string length $declared") {
                DBusMarshaller.unmarshal("s", bytes, 0, Endian.LITTLE)
            }
        }
    }

    /**
     * The worst of the set, because it never threw: an array whose declared byte-length narrows to
     * a negative [Int] made `end < offset`, the fill loop never ran, and the caller got a
     * successful decode of an EMPTY array indistinguishable from a genuinely empty one. Silent
     * peer-driven data loss, reachable with no hostile intent at all.
     */
    @Test
    fun arrayLengthThatNarrowsNegative_isRejectedRatherThanSilentlyDecodingToEmpty() {
        for (declared in listOf(0xFFFFFFF0L, 0x80000000L)) {
            val bytes = littleEndianU32(declared) + byteArrayOf(1, 2, 3, 4)

            assertFailsWith<DBusMarshallingException>("array length $declared") {
                DBusMarshaller.unmarshal("ay", bytes, 0, Endian.LITTLE)
            }
        }
    }

    // --- nesting ----------------------------------------------------------------------------------

    /**
     * A variant nesting level costs 3 bytes on the wire (`0x01 'v' 0x00`), so a ~30 KB body reaches
     * 10,000 frames of recursion in the demarshaller.
     */
    @Test
    fun nestedVariantsBeyondSpecDepth_areRejectedInsteadOfOverflowingTheStack() {
        assertFailsWith<DBusMarshallingException> {
            DBusMarshaller.unmarshal("v", nestedVariantBody(10_000), 0, Endian.LITTLE)
        }
    }

    /** The depth cap is the spec's, so nesting the spec permits must still decode. */
    @Test
    fun nestedVariantsWithinSpecDepth_stillDecode() {
        val result = DBusMarshaller.unmarshal("v", nestedVariantBody(30), 0, Endian.LITTLE)

        var payload = result.values.single() as Message.JvmVariantPayload
        repeat(30) { payload = payload.value as Message.JvmVariantPayload }
        assertEquals(42u.toUByte(), payload.value)
    }

    // --- header-field types -----------------------------------------------------------------------

    @Test
    fun headerFieldCarryingTheWrongVariantType_isRejectedAsAMarshallingFailure() {
        val pathAsUInt = frameWithHeaderFields(listOf(headerField(WireHeaderField.PATH, "u", 7u)))
        val memberAsByteArray = frameWithHeaderFields(
            listOf(headerField(WireHeaderField.MEMBER, "ay", listOf(1u.toUByte())))
        )

        assertFailsWith<DBusMarshallingException>("PATH as 'u'") {
            WireMessageCodec.decode(pathAsUInt)
        }
        assertFailsWith<DBusMarshallingException>("MEMBER as 'ay'") {
            WireMessageCodec.decode(memberAsByteArray)
        }
    }

    // --- the umbrella failure ---------------------------------------------------------------------

    /**
     * A hostile peer sends exactly ONE frame the parser cannot handle. The frame itself is fatal
     * either way — the stream is framed, so a failed parse leaves the byte offset unknown and
     * nothing further can be decoded — but the connection must then REPORT that it is dead.
     * Previously the reader thread just vanished (only [IOException] and [DBusMarshallingException]
     * were caught) while `running` stayed true and the socket stayed open, so every later call sat
     * out its full timeout against a black hole.
     */
    @Test
    fun hostileFrameFromAPeer_failsTheConnectionVisiblyInsteadOfSilently() {
        val frame = frameWithHeaderFields(listOf(headerField(WireHeaderField.PATH, "u", 7u)))
        HostilePeer(frame).use { peer ->
            val connection = DBusWireConnection.connectDirect(peer.address)
            try {
                assertTrue(connection.isReaderRunning, "reader should run right after connect")
                assertTrue(peer.awaitFrameSent(), "the hostile peer should have sent its frame")
                awaitReaderStopped(connection)

                val start = System.nanoTime()
                val failure = assertFailsWith<IOException> {
                    connection.callBlocking(
                        WireMessage(
                            type = WireMessageType.METHOD_CALL,
                            path = "/",
                            interfaceName = "org.freedesktop.DBus.Peer",
                            member = "Ping"
                        ),
                        timeoutMillis = 5_000
                    )
                }
                val elapsedMillis = (System.nanoTime() - start) / 1_000_000
                assertTrue(
                    elapsedMillis < 2_000,
                    "a call on a dead connection must fail fast, took ${elapsedMillis}ms: $failure"
                )
            } finally {
                connection.close()
            }
        }
    }

    private fun awaitReaderStopped(connection: DBusWireConnection) {
        val deadline = System.currentTimeMillis() + 5_000
        while (connection.isReaderRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertFalse(connection.isReaderRunning, "the reader thread should have stopped")
    }

    // --- frame construction -------------------------------------------------------------------------

    private fun littleEndianU32(value: Long): ByteArray =
        ByteArray(4) { ((value shr (8 * it)) and 0xff).toByte() }

    /**
     * The 16 bytes that open every frame: the fixed header plus the header-fields array length
     * prefix. That is all [WireMessageCodec.read] needs to decide how much more to allocate, so a
     * length defect is reachable from these bytes alone.
     */
    private fun framePrefix(bodyLength: Long, fieldsLength: Long): ByteArray = byteArrayOf(
        Endian.LITTLE.code,
        WireMessageType.METHOD_RETURN.code.toByte(),
        0,
        1
    ) + littleEndianU32(bodyLength) + littleEndianU32(1L) + littleEndianU32(fieldsLength)

    private fun headerField(code: Int, variantSignature: String, value: Any?) =
        Message.JvmStructPayload(
            "(yv)",
            listOf(code.toUByte(), Message.JvmVariantPayload(variantSignature, value))
        )

    /** A complete, well-framed METHOD_RETURN whose header fields are whatever [fields] says. */
    private fun frameWithHeaderFields(fields: List<Message.JvmStructPayload>): ByteArray {
        val writer = DBusWriter(Endian.LITTLE)
        writer.marshal(
            DBusSignatureParser.parse("yyyyuua(yv)"),
            listOf(
                Endian.LITTLE.code,
                WireMessageType.METHOD_RETURN.code.toUByte(),
                0.toUByte(),
                1.toUByte(),
                0u,
                1u,
                fields
            )
        )
        writer.align(8)
        return writer.toByteArray()
    }

    /** [depth] nested variants wrapping a single byte: `(0x01 'v' 0x00) * depth`, then `y` = 42. */
    private fun nestedVariantBody(depth: Int): ByteArray {
        val body = ByteArray(depth * 3 + 4)
        for (level in 0 until depth) {
            body[level * 3] = 1
            body[level * 3 + 1] = 'v'.code.toByte()
            body[level * 3 + 2] = 0
        }
        val tail = depth * 3
        body[tail] = 1
        body[tail + 1] = 'y'.code.toByte()
        body[tail + 2] = 0
        body[tail + 3] = 42
        return body
    }
}

/**
 * A fake AF_UNIX D-Bus peer: it completes the SERVER side of SASL EXTERNAL (+ `AGREE_UNIX_FD` +
 * `BEGIN`), writes one hand-crafted [frame], and then holds the socket OPEN until [close]. Holding
 * it open is the point — it is what distinguishes "the peer hung up" (which the reader has always
 * reported) from "the peer is still there and we can no longer understand it".
 */
private class HostilePeer(private val frame: ByteArray) : AutoCloseable {
    private val file = File.createTempFile("sdbus-hostile-", ".sock", File("/tmp"))
        .also { it.delete() }
    private val server = AFUNIXServerSocket.bindOn(AFUNIXSocketAddress.of(file))
    private val frameSent = CountDownLatch(1)
    private val stop = CountDownLatch(1)

    val address: String = "unix:path=${file.absolutePath}"

    private val acceptor = thread(isDaemon = true, name = "sdbus-hostile-peer") {
        runCatching { server.accept().use(::serve) }
    }

    fun awaitFrameSent(): Boolean = frameSent.await(10, TimeUnit.SECONDS)

    private fun serve(socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        input.read() // the mandatory NUL that opens the auth conversation
        readLine(input) // AUTH EXTERNAL <uid-hex>
        writeLine(output, "OK 0123456789abcdef0123456789abcdef")
        readLine(input) // NEGOTIATE_UNIX_FD
        writeLine(output, "AGREE_UNIX_FD")
        readLine(input) // BEGIN
        output.write(frame)
        output.flush()
        frameSent.countDown()
        stop.await()
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        var previous = -1
        while (true) {
            val c = input.read()
            if (c < 0) return line.toString()
            if (previous == '\r'.code && c == '\n'.code) return line.dropLast(1).toString()
            line.append(c.toChar())
            previous = c
        }
    }

    private fun writeLine(output: OutputStream, line: String) {
        output.write((line + "\r\n").toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    override fun close() {
        stop.countDown()
        runCatching { server.close() }
        runCatching { acceptor.join(2_000) }
        file.delete()
    }
}
