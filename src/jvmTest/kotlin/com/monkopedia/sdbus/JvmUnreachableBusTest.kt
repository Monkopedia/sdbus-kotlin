package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the issue #81 fix: when the bus is unreachable, the JVM connection factories must THROW
 * a [com.monkopedia.sdbus.Error] like the native backend (whose openBus fails with
 * "Failed to open bus") -- never silently fall back to the in-process stub backend
 * (`StubJvmDbusBackend`, unique name `:jvm-stub`) and fake a successful connection.
 *
 * The exact D-Bus error *name* is not pinned across backends because dbus-java does not expose
 * the underlying errno that sd-bus would surface; the contract is the Error type and the throw.
 */
class JvmUnreachableBusTest {

    private fun unreachableAddress(): String =
        "unix:path=/nonexistent/sdbus-kotlin-test-${System.nanoTime()}"

    // createSessionBusConnection(address) is the deterministic way to point a session-bus
    // connection at an address that goes nowhere, independent of the DBUS_SESSION_BUS_ADDRESS
    // the test runner itself was started with.
    @Test
    fun createSessionBusConnection_withUnreachableAddress_throwsInsteadOfStubFallback() {
        val error = assertFailsWith<Error> {
            createSessionBusConnection(unreachableAddress())
        }
        assertTrue(
            error.errorMessage.contains("Failed to open bus"),
            "expected a bus-open failure, got name='${error.name}' " +
                "message='${error.errorMessage}'"
        )
    }

    @Test
    fun createDirectBusConnection_withUnreachableAddress_throwsError() {
        val error = assertFailsWith<Error> {
            createDirectBusConnection(unreachableAddress())
        }
        assertTrue(
            error.errorMessage.contains("Failed to open bus"),
            "expected a bus-open failure, got name='${error.name}' " +
                "message='${error.errorMessage}'"
        )
    }
}
