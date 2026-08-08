package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.createDirectBusConnection
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * On a brokerless DIRECT connection nothing stamps the `sender` field: there is no daemon, so the
 * value is whatever the peer put in the frame. The credential accessors must therefore report
 * *no* credentials for such a message rather than the credentials of some locally-known name the
 * sender string happens to equal. Correctness contract for #199.
 */
class JvmDirectSenderCredentialsTest {

    @Test
    fun credentialsAreAbsentForAPeerSuppliedSenderOnADirectConnection() {
        val iface = InterfaceName("com.monkopedia.sdbus.spike.Iface")
        val member = "Ping"
        val path = "/com/monkopedia/sdbus/spike"
        val wellKnown = ServiceName("com.monkopedia.sdbus.spike.Service")

        val localUid = com.sun.security.auth.module.UnixSystem().uid.toUInt()

        DirectSignalPeer().use { peer ->
            val connection = createDirectBusConnection(peer.address)
            // In direct mode RequestName is synthesized as PRIMARY_OWNER, which is what publishes
            // the well-known name into the process-wide local-name registry.
            connection.requestName(wellKnown)

            val delivered = CountDownLatch(1)
            val reportedUid = AtomicReference<UInt?>(null)
            val reportedPid = AtomicReference<Int?>(null)

            val match = connection.addMatch(
                "type='signal',interface='${iface.value}',member='$member'"
            ) { message: Message ->
                reportedUid.set(runCatching { message.credsUid }.getOrNull())
                reportedPid.set(runCatching { message.credsPid }.getOrNull())
                delivered.countDown()
            }

            try {
                peer.awaitConnected()
                peer.emit(
                    WireMessage(
                        type = WireMessageType.SIGNAL,
                        serial = 1,
                        path = path,
                        interfaceName = iface.value,
                        member = member,
                        // The peer picks this freely — no daemon rewrites it on a direct socket.
                        sender = wellKnown.value,
                        signature = "",
                        body = emptyList()
                    )
                )
                assertTrue(
                    delivered.await(10, TimeUnit.SECONDS),
                    "the signal was never delivered to the match handler"
                )

                assertNull(
                    reportedUid.get(),
                    "Message.credsUid reported ${reportedUid.get()} for a signal on a DIRECT " +
                        "connection whose sender field was supplied by the peer; this JVM " +
                        "process runs as uid $localUid. A direct connection has no authoritative " +
                        "sender, so no uid should be reported at all."
                )
                assertNull(
                    reportedPid.get(),
                    "Message.credsPid reported ${reportedPid.get()} for a peer-supplied sender " +
                        "on a DIRECT connection; this JVM process is pid " +
                        "${ProcessHandle.current().pid()}."
                )
            } finally {
                match.release()
                connection.release()
            }
        }
    }
}

/**
 * A minimal brokerless D-Bus peer: an AF_UNIX listener that completes the SASL EXTERNAL handshake
 * and can then push arbitrary frames down the socket. Same shape as the peer in
 * `JvmDirectConnectionIdentityTest`, plus [emit].
 */
private class DirectSignalPeer : Closeable {
    private val directory: Path = Files.createTempDirectory("sdbus-direct-creds")
    private val socketPath: Path = directory.resolve("peer")
    private val listener: ServerSocketChannel =
        ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            .apply { bind(UnixDomainSocketAddress.of(socketPath)) }
    private val connected = CountDownLatch(1)
    private val stream = AtomicReference<OutputStream?>(null)

    val address: String = "unix:path=$socketPath"

    private val acceptor = thread(start = true, isDaemon = true, name = "sdbus-spike-direct-peer") {
        while (true) {
            val channel = try {
                listener.accept()
            } catch (_: IOException) {
                return@thread
            }
            thread(start = true, isDaemon = true, name = "sdbus-spike-direct-peer-sasl") {
                runCatching { handshake(channel) }
            }
        }
    }

    fun awaitConnected() {
        check(connected.await(10, TimeUnit.SECONDS)) { "peer handshake did not complete" }
    }

    fun emit(message: WireMessage) {
        val output = checkNotNull(stream.get()) { "peer is not connected" }
        synchronized(this) {
            output.write(WireMessageCodec.encode(message))
            output.flush()
        }
    }

    private fun handshake(channel: SocketChannel) {
        val input = Channels.newInputStream(channel)
        val output = Channels.newOutputStream(channel)
        fun readLine(): String = buildString {
            while (!endsWith("\r\n")) {
                val c = input.read()
                if (c < 0) return@buildString
                append(c.toChar())
            }
        }.removeSuffix("\r\n")
        fun writeLine(line: String) {
            output.write("$line\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }

        input.read() // the mandatory leading NUL
        readLine() // AUTH EXTERNAL <uid>
        writeLine("OK 0123456789abcdef0123456789abcdef")
        readLine() // NEGOTIATE_UNIX_FD
        writeLine("AGREE_UNIX_FD")
        readLine() // BEGIN
        stream.set(output)
        connected.countDown()
    }

    override fun close() {
        runCatching { listener.close() }
        acceptor.join(1_000)
        runCatching { Files.deleteIfExists(socketPath) }
        runCatching { Files.deleteIfExists(directory) }
    }
}
