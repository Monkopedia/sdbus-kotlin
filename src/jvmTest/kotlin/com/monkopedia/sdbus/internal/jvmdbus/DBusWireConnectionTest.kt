package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.JvmUnixFdSupport
import com.monkopedia.sdbus.UnixFd
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /**
     * Regression for issue #101 (bounded serve pool must not deadlock on nested same-connection
     * dispatch). A served "Outer" handler makes a BLOCKING call back to "Inner" on the SAME
     * connection: Outer runs on a serve worker and PARKS waiting for Inner's reply, and Inner needs
     * its own serve worker. We fire MORE concurrent Outer calls than the pool's [serveBound] so that
     * — with a naive fixed bound — every worker would be parked on an Outer and the queued Inner
     * calls could never get a thread, deadlocking. The bounded pool's compensation must instead let
     * every chain finish. [withTimeout] makes a regression FAIL FAST rather than hang.
     */
    @Test
    fun nestedSameConnectionDispatch_underBoundedPool_completesWithoutDeadlock() = runBlocking {
        val server = connectOrNull() ?: return@runBlocking
        val client = connectOrNull() ?: run {
            server.close()
            return@runBlocking
        }
        val busName = "com.monkopedia.sdbus.wire.nest.s${System.nanoTime()}"
        val path = "/com/monkopedia/sdbus/wire/nest"
        val iface = "com.monkopedia.sdbus.wire.Nest"

        server.setIncomingCallHandler { message ->
            when (message.member) {
                "Inner" -> server.send(
                    WireMessage(
                        type = WireMessageType.METHOD_RETURN,
                        replySerial = message.serial,
                        destination = message.sender,
                        signature = "b",
                        body = listOf(true)
                    )
                )

                "Outer" -> {
                    // Nested call on THIS same connection: parks this serve worker until the reader
                    // delivers Inner's reply, which itself must be served on another worker.
                    val innerOk = runCatching {
                        server.callBlocking(
                            WireMessage(
                                type = WireMessageType.METHOD_CALL,
                                destination = busName,
                                path = path,
                                interfaceName = iface,
                                member = "Inner"
                            )
                        ).body.firstOrNull() == true
                    }.getOrDefault(false)
                    server.send(
                        WireMessage(
                            type = WireMessageType.METHOD_RETURN,
                            replySerial = message.serial,
                            destination = message.sender,
                            signature = "b",
                            body = listOf(innerOk)
                        )
                    )
                }
            }
        }

        try {
            assertEquals(1, server.requestName(busName), "server should own $busName")
            // Strictly exceed the pool bound so a fixed-bound pool would have no spare worker for the
            // nested Inner calls (every worker parked on an Outer) -> deadlock without compensation.
            val concurrency = server.serveBound + 8
            val replies = withTimeout(20_000) {
                (1..concurrency).map {
                    async(Dispatchers.IO) {
                        client.call(
                            WireMessage(
                                type = WireMessageType.METHOD_CALL,
                                destination = busName,
                                path = path,
                                interfaceName = iface,
                                member = "Outer"
                            )
                        )
                    }
                }.awaitAll()
            }
            assertEquals(concurrency, replies.size)
            assertTrue(
                replies.all { it.body.firstOrNull() == true },
                "every nested Outer->Inner chain should complete successfully"
            )
        } finally {
            client.close()
            server.close()
        }
    }

    /**
     * The serve pool must NOT grow a thread per concurrently-served call (the old
     * newCachedThreadPool behavior, issue #101). A flood of independent slow (non-nested) handlers,
     * far exceeding [serveBound], must all complete while the live worker count stays capped at
     * [serveBound]: the surplus calls queue instead of spawning unbounded threads.
     */
    @Test
    fun serveWorkerPool_underFloodOfSlowHandlers_staysBounded() = runBlocking {
        val server = connectOrNull() ?: return@runBlocking
        val client = connectOrNull() ?: run {
            server.close()
            return@runBlocking
        }
        val busName = "com.monkopedia.sdbus.wire.flood.s${System.nanoTime()}"
        val path = "/com/monkopedia/sdbus/wire/flood"
        val iface = "com.monkopedia.sdbus.wire.Flood"

        server.setIncomingCallHandler { message ->
            if (message.member == "Slow") {
                // Slow, but INDEPENDENT: no call back on this connection, so it never compensates.
                Thread.sleep(200)
                server.send(
                    WireMessage(
                        type = WireMessageType.METHOD_RETURN,
                        replySerial = message.serial,
                        destination = message.sender,
                        signature = "b",
                        body = listOf(true)
                    )
                )
            }
        }

        try {
            assertEquals(1, server.requestName(busName), "server should own $busName")
            val concurrency = server.serveBound * 4
            val replies = withTimeout(30_000) {
                (1..concurrency).map {
                    async(Dispatchers.IO) {
                        client.call(
                            WireMessage(
                                type = WireMessageType.METHOD_CALL,
                                destination = busName,
                                path = path,
                                interfaceName = iface,
                                member = "Slow"
                            )
                        )
                    }
                }.awaitAll()
            }
            assertEquals(concurrency, replies.size)
            assertTrue(replies.all { it.body.firstOrNull() == true }, "all slow calls should reply")
            assertTrue(
                server.serveLargestPoolSize <= server.serveBound,
                "serve pool grew to ${server.serveLargestPoolSize} threads, exceeding the bound " +
                    "of ${server.serveBound}: a flood of slow handlers must queue, not spawn " +
                    "unbounded threads"
            )
        } finally {
            client.close()
            server.close()
        }
    }

    /**
     * Watchdog backstop for the case the same-thread nested compensation MISSES (issue #101
     * follow-up): a served handler that blocks its worker on a dependency satisfied only by ANOTHER
     * served call on this connection. Here `bound` "Gate" handlers each park their worker on a shared
     * latch; the only thing that can open that latch is a separate "Open" call — which, once every
     * worker is parked, has no worker to run on and sits in the queue. [beginNestedBlock] never fires
     * (no worker is inside [DBusWireConnection.call]/[callBlocking]), so without the saturation
     * watchdog this deadlocks: the pool is fully parked, the queue is non-empty, and NO task is making
     * forward progress. The watchdog detects the forward-progress stall (queue non-empty + no task
     * start/completion for the stall window) and spawns a bounded compensating worker, which runs
     * "Open", releases the latch, and lets every "Gate" — and "Open" itself — complete. On the
     * pre-watchdog code the client [withTimeout] fires first and the test FAILS FAST instead of
     * hanging. One compensating worker suffices regardless of width: the single "Open" releases all
     * waiters, so this stays bounded.
     */
    @Test
    fun externalLatchDependency_underBoundedPool_isRescuedByWatchdog() = runBlocking {
        val server = connectOrNull() ?: return@runBlocking
        val client = connectOrNull() ?: run {
            server.close()
            return@runBlocking
        }
        val busName = "com.monkopedia.sdbus.wire.gate.s${System.nanoTime()}"
        val path = "/com/monkopedia/sdbus/wire/gate"
        val iface = "com.monkopedia.sdbus.wire.Gate"

        val bound = server.serveBound
        // Counts down as each Gate handler enters and is about to park: lets the test fire "Open"
        // only once every worker is genuinely parked, making the starvation deterministic.
        val allParked = CountDownLatch(bound)
        // Opened solely by the "Open" handler — which itself needs a worker the parked pool lacks.
        val gate = CountDownLatch(1)

        server.setIncomingCallHandler { message ->
            when (message.member) {
                "Gate" -> {
                    allParked.countDown()
                    // Park this worker until "Open" releases the gate. A generous internal timeout so
                    // that on BROKEN code the client-side withTimeout below fires first (fail fast),
                    // not this await returning a spurious false.
                    val released = gate.await(60, TimeUnit.SECONDS)
                    server.send(
                        WireMessage(
                            type = WireMessageType.METHOD_RETURN,
                            replySerial = message.serial,
                            destination = message.sender,
                            signature = "b",
                            body = listOf(released)
                        )
                    )
                }

                "Open" -> {
                    gate.countDown()
                    server.send(
                        WireMessage(
                            type = WireMessageType.METHOD_RETURN,
                            replySerial = message.serial,
                            destination = message.sender,
                            signature = "b",
                            body = listOf(true)
                        )
                    )
                }
            }
        }

        try {
            assertEquals(1, server.requestName(busName), "server should own $busName")
            // Saturate every worker with a parked Gate handler.
            val gateReplies = (1..bound).map {
                async(Dispatchers.IO) {
                    client.call(
                        WireMessage(
                            type = WireMessageType.METHOD_CALL,
                            destination = busName,
                            path = path,
                            interfaceName = iface,
                            member = "Gate"
                        )
                    )
                }
            }
            // Only once every worker is parked is "Open" guaranteed to be queued with no free worker —
            // the exact starvation the watchdog must rescue.
            assertTrue(
                allParked.await(10, TimeUnit.SECONDS),
                "all $bound Gate handlers should park before Open is sent"
            )
            val openReply = async(Dispatchers.IO) {
                client.call(
                    WireMessage(
                        type = WireMessageType.METHOD_CALL,
                        destination = busName,
                        path = path,
                        interfaceName = iface,
                        member = "Open"
                    )
                )
            }

            // On broken (pre-watchdog) code Open never runs, the gate never opens, and this times out.
            val replies = withTimeout(15_000) { (gateReplies + openReply).awaitAll() }
            assertEquals(bound + 1, replies.size)
            assertTrue(
                replies.all { it.body.firstOrNull() == true },
                "every Gate must observe the gate opened, and Open must succeed"
            )
        } finally {
            gate.countDown()
            client.close()
            server.close()
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
