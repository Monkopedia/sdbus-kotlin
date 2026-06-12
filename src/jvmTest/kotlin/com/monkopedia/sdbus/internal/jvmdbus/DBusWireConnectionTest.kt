package com.monkopedia.sdbus.internal.jvmdbus

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
