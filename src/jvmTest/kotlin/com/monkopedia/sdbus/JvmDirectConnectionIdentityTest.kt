package com.monkopedia.sdbus

import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A brokerless DIRECT connection has no daemon to hand it a unique name, and the JVM backend used
 * to substitute one shared constant (`":jvm-wire"`) for every such connection. Since the in-process
 * dispatch table is keyed by that name, two direct connections in one JVM shared an identity and
 * objects they exported at the same path clobbered each other. Parity item from #141.
 */
class JvmDirectConnectionIdentityTest {

    @Test
    fun twoDirectConnectionsHaveDistinctUniqueNames() {
        DirectPeer().use { first ->
            DirectPeer().use { second ->
                val a = createDirectBusConnection(first.address)
                val b = createDirectBusConnection(second.address)
                try {
                    assertNotEquals(b.uniqueName, a.uniqueName)
                } finally {
                    a.release()
                    b.release()
                }
            }
        }
    }

    @Test
    fun objectsOnTwoDirectConnectionsDoNotClobberEachOther() {
        val path = ObjectPath("/com/monkopedia/sdbus/direct")
        val iface = InterfaceName("com.monkopedia.sdbus.direct.Iface")
        val whoAmI = MethodName("WhoAmI")

        DirectPeer().use { first ->
            DirectPeer().use { second ->
                val a = createDirectBusConnection(first.address)
                val b = createDirectBusConnection(second.address)
                val objA = createObject(a, path)
                val objB = createObject(b, path)
                val regA = objA.addVTable(iface) {
                    method(whoAmI) { call<String> { "a" } }
                }
                val regB = objB.addVTable(iface) {
                    method(whoAmI) { call<String> { "b" } }
                }
                val proxyA = createProxy(a, ServiceName(a.uniqueName.value), path)
                val proxyB = createProxy(b, ServiceName(b.uniqueName.value), path)

                try {
                    // Each connection must reach its OWN object; before the fix both registered
                    // under ":jvm-wire", so the second export overwrote the first and both proxies
                    // answered "b".
                    assertEquals("a", proxyA.callMethod<String>(iface, whoAmI) { call() })
                    assertEquals("b", proxyB.callMethod<String>(iface, whoAmI) { call() })
                } finally {
                    proxyA.release()
                    proxyB.release()
                    regA.release()
                    regB.release()
                    objA.release()
                    objB.release()
                    a.release()
                    b.release()
                }
            }
        }
    }
}

/**
 * A minimal brokerless D-Bus peer: an AF_UNIX listener that completes the SASL EXTERNAL handshake
 * (`NUL` / `AUTH` / `NEGOTIATE_UNIX_FD` / `BEGIN`) and then stays silent. Enough for
 * [createDirectBusConnection] to establish a direct connection, which — unlike a bus connection —
 * never sends `Hello`.
 */
private class DirectPeer : Closeable {
    private val directory: Path = Files.createTempDirectory("sdbus-direct")
    private val socketPath: Path = directory.resolve("peer")
    private val listener: ServerSocketChannel =
        ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            .apply { bind(UnixDomainSocketAddress.of(socketPath)) }

    val address: String = "unix:path=$socketPath"

    private val acceptor = thread(start = true, isDaemon = true, name = "sdbus-test-direct-peer") {
        while (true) {
            val channel = try {
                listener.accept()
            } catch (_: IOException) {
                return@thread
            }
            thread(start = true, isDaemon = true, name = "sdbus-test-direct-peer-sasl") {
                runCatching { handshake(channel) }
            }
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
        // The client sends no further traffic on a direct connection it does not call over, and
        // this peer never initiates any, so the socket just idles until the test closes it.
    }

    override fun close() {
        runCatching { listener.close() }
        acceptor.join(1_000)
        runCatching { Files.deleteIfExists(socketPath) }
        runCatching { Files.deleteIfExists(directory) }
    }
}
