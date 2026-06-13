package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.JvmUnixFdSupport
import com.monkopedia.sdbus.UnixFd
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * End-to-end tests for the owned low-level D-Bus connection (epic #93, phase 2) against a real
 * session bus. When no bus is reachable the connection factory throws [IOException]; we treat that
 * as a skip so the suite stays green outside `dbus-run-session`.
 */
class DBusWireConnectionTest {

    private fun connectOrNull(): DBusWireConnection? = try {
        DBusWireConnection.connectSession()
    } catch (_: IOException) {
        null
    }

    @Test
    fun connect_completesSaslAndHello_assignsUniqueName() {
        val connection = connectOrNull() ?: return
        try {
            val unique = connection.uniqueName
            assertNotNull(unique, "Hello should assign a unique name")
            assertTrue(
                Regex(":1\\.\\d+").matches(unique),
                "Unique name should look like :1.x, was $unique"
            )
            assertTrue(connection.isReaderRunning, "Reader thread should be running after connect")
        } finally {
            connection.close()
        }
    }

    @Test
    fun call_listNames_parsesReplyContainingDbusDaemon() = runBlocking {
        val connection = connectOrNull() ?: return@runBlocking
        try {
            val reply = withTimeout(5_000) {
                connection.call(
                    WireMessage(
                        type = WireMessageType.METHOD_CALL,
                        destination = "org.freedesktop.DBus",
                        path = "/org/freedesktop/DBus",
                        interfaceName = "org.freedesktop.DBus",
                        member = "ListNames"
                    )
                )
            }
            assertEquals("as", reply.signature)
            @Suppress("UNCHECKED_CAST")
            val names = reply.body.first() as List<String>
            assertTrue(
                names.contains("org.freedesktop.DBus"),
                "ListNames should include the bus daemon, got $names"
            )
            assertTrue(
                names.contains(connection.uniqueName),
                "ListNames should include our own unique name"
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun call_getId_returnsBusId() = runBlocking {
        val connection = connectOrNull() ?: return@runBlocking
        try {
            val reply = withTimeout(5_000) {
                connection.call(
                    WireMessage(
                        type = WireMessageType.METHOD_CALL,
                        destination = "org.freedesktop.DBus",
                        path = "/org/freedesktop/DBus",
                        interfaceName = "org.freedesktop.DBus",
                        member = "GetId"
                    )
                )
            }
            assertEquals("s", reply.signature)
            val id = reply.body.first() as String
            assertTrue(id.isNotEmpty(), "Bus id should be non-empty")
        } finally {
            connection.close()
        }
    }

    @Test
    fun addMatch_thenRequestName_deliversNameOwnerChangedSignal() = runBlocking {
        val connection = connectOrNull() ?: return@runBlocking
        val busName = "com.monkopedia.sdbus.wire.test.s${System.nanoTime()}"
        val signalSeen = CompletableDeferred<WireMessage>()
        val subscription = connection.onSignal { message ->
            if (message.member == "NameOwnerChanged" &&
                message.body.firstOrNull() == busName &&
                !signalSeen.isCompleted
            ) {
                signalSeen.complete(message)
            }
        }
        try {
            connection.addMatch(
                "type='signal',interface='org.freedesktop.DBus',member='NameOwnerChanged'"
            )
            val result = connection.requestName(busName)
            assertEquals(1, result, "Should become the primary owner of $busName")

            val signal = withTimeout(5_000) { signalSeen.await() }
            assertEquals("NameOwnerChanged", signal.member)
            assertEquals(busName, signal.body[0])
            // NameOwnerChanged is (name, oldOwner, newOwner); the new owner is our unique name.
            assertEquals(connection.uniqueName, signal.body[2])
        } finally {
            subscription.close()
            connection.close()
        }
    }

    /**
     * Real SCM_RIGHTS round-trip closing #83: a UnixFd is sent from one owned connection to another
     * as a method-call argument; the fd genuinely traverses the dbus daemon (which relays passed
     * fds via SCM_RIGHTS, dup-ing them into a separate process and back), so the descriptor the
     * server receives is a LIVE duplicate of the original pipe read-end -- proven by reading, from
     * the received fd, the exact bytes written into the original write-end before the call.
     */
    @Test
    fun unixFd_roundTripsAcrossConnections_throughDaemon_asLiveDuplicate() = runBlocking {
        if (!JvmUnixFdSupport.supportsFdDuplicationSemantics) return@runBlocking
        val server = connectOrNull() ?: return@runBlocking
        val client = connectOrNull() ?: run {
            server.close()
            return@runBlocking
        }
        if (!server.unixFdNegotiated || !client.unixFdNegotiated) {
            client.close()
            server.close()
            return@runBlocking
        }
        val pipe = JvmUnixFdSupport.createPipePair() ?: run {
            client.close()
            server.close()
            return@runBlocking
        }
        val (readFd, writeFd) = pipe
        val busName = "com.monkopedia.sdbus.wire.fdtest.s${System.nanoTime()}"
        val path = "/com/monkopedia/sdbus/wire/fdtest"
        val iface = "com.monkopedia.sdbus.wire.FdTest"
        val payload = "live-dup-through-scm-rights"
        val receivedBytes = CompletableDeferred<String>()

        server.setIncomingCallHandler { message ->
            try {
                if (message.member == "SendFd" && message.interfaceName == iface) {
                    val received = message.body.firstOrNull() as? UnixFd
                    if (received == null) {
                        receivedBytes.completeExceptionally(
                            AssertionError("server did not receive a UnixFd; body=${message.body}")
                        )
                    } else {
                        receivedBytes.complete(readFromFd(received.fd, 128))
                        server.send(
                            WireMessage(
                                type = WireMessageType.METHOD_RETURN,
                                replySerial = message.serial,
                                destination = message.sender,
                                signature = "b",
                                body = listOf(true)
                            )
                        )
                        received.release()
                    }
                }
            } catch (t: Throwable) {
                receivedBytes.completeExceptionally(t)
            }
        }

        try {
            assertEquals(1, server.requestName(busName), "server should own $busName")
            // Buffer the known bytes in the pipe before sending the read-end, so the server can
            // read them straight out of the received duplicate.
            writeToFd(writeFd, payload)
            val sent = UnixFd(readFd) // duplicates; we retain ownership of readFd
            try {
                val reply = withTimeout(5_000) {
                    client.call(
                        WireMessage(
                            type = WireMessageType.METHOD_CALL,
                            destination = busName,
                            path = path,
                            interfaceName = iface,
                            member = "SendFd",
                            signature = "h",
                            body = listOf(sent)
                        )
                    )
                }
                assertEquals("b", reply.signature)
                assertEquals(true, reply.body.first(), "server should acknowledge the fd")
                assertEquals(
                    payload,
                    withTimeout(5_000) { receivedBytes.await() },
                    "the received fd must be a live duplicate of the pipe read-end"
                )
            } finally {
                sent.release()
            }
        } finally {
            JvmUnixFdSupport.closeFd(readFd)
            JvmUnixFdSupport.closeFd(writeFd)
            client.close()
            server.close()
        }
    }

    // Writes [text] to a duplicate of [fd] so the original descriptor is left intact for the caller.
    private fun writeToFd(fd: Int, text: String) {
        val dup = JvmUnixFdSupport.checkedDup(fd)
        FileOutputStream(JvmUnixFdSupport.descriptorForFd(dup)).use {
            it.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    // Reads up to [max] bytes from a duplicate of [fd], leaving the original descriptor intact.
    private fun readFromFd(fd: Int, max: Int): String {
        val dup = JvmUnixFdSupport.checkedDup(fd)
        return FileInputStream(JvmUnixFdSupport.descriptorForFd(dup)).use { stream ->
            val buffer = ByteArray(max)
            val read = stream.read(buffer)
            String(buffer, 0, read.coerceAtLeast(0), Charsets.UTF_8)
        }
    }

    @Test
    fun close_stopsReaderThreadAndClosesSocket() {
        val connection = connectOrNull() ?: return
        assertTrue(connection.isReaderRunning, "Reader should run while open")

        connection.close()

        // Give the daemon thread a moment to observe the closed socket and exit.
        val deadline = System.currentTimeMillis() + 2_000
        while (connection.isReaderRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertFalse(connection.isReaderRunning, "Reader thread should stop after close")
    }
}
