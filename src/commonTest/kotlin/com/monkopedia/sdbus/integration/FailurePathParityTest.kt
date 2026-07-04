package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * SdbusException / failure-path consistency tests. These assert that BOTH backends (the native sd-bus
 * backend exercised by the linux*Test targets and the pure-JVM backend exercised by jvmTest)
 * surface failures the same way: a timeout becomes a timeout [SdbusException], a server-thrown named
 * D-Bus error round-trips its name + message, a wrong-argument-type / signature mismatch becomes
 * an [SdbusException] rather than reaching the handler, and tearing a connection down stays consistent
 * without crashing or hanging.
 *
 * Because the suite is in commonTest it is compiled and run against every target, so a backend
 * divergence shows up as a per-target test failure.
 *
 * NOTE on teardown: connection teardown / event-loop shutdown is the project's known native-hang
 * area. Every teardown assertion here is wrapped in [withTimeout] so that a hang surfaces as a
 * deterministic test failure instead of an indefinitely stuck suite.
 */
class FailurePathParityTest {

    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.failure.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/failure/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    // Always true; indirection keeps the throwing handler's inferred return type Int (a bare
    // `throw` would infer Nothing, which has no serializer at vtable registration).
    private val alwaysThrows = true

    // --- method-call timeout ---------------------------------------------------------------

    // A server method that deliberately sleeps far past the client's per-call timeout must
    // surface as a timeout SdbusException on both backends -- not a hang, and not some other error type.
    @Test
    fun methodCallTimeout_surfacesTimeoutError() = runBlocking {
        val ids = uniqueFixtureIds("timeout")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        // The handler delays past the client's per-call timeout, then completes. We keep both
        // event loops running until after it has finished so its (now-late) reply is sent while
        // the connection is still up -- on the native backend, a reply send into an already
        // torn-down loop throws an uncaught ENOTCONN that crashes the test process.
        val serverDelayMs = 600L
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Slow")) {
                asyncCall { value: Int ->
                    delay(serverDelayMs)
                    value
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = withTimeout(3_000) {
                assertFailsWith<SdbusException> {
                    proxy.callMethodAsync<Int>(ids.iface, MethodName("Slow")) {
                        call(1)
                        timeout = 100.milliseconds
                    }
                }
            }
            // The backends differ on whether the daemon reports Timeout or NoReply, and on the
            // exact human-readable string, so assert membership rather than an exact value -- but
            // require that it is recognizably a timeout, never a generic/unrelated error.
            assertTrue(
                thrown.name.contains("Timeout") ||
                    thrown.name.contains("NoReply") ||
                    thrown.errorMessage.contains("timed out", ignoreCase = true),
                "expected a timeout-shaped error, got name='${thrown.name}' " +
                    "message='${thrown.errorMessage}'"
            )
            // Let the slow handler finish and flush its late reply while loops are still alive.
            delay(serverDelayMs + 400)
        } finally {
            withTimeout(5_000) {
                proxyConnection.stopEventLoop()
                serverConnection.stopEventLoop()
            }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // The connection-level default timeout (Connection.methodCallTimeout) must apply to calls
    // made WITHOUT an explicit per-call timeout on both backends (issue #80): native wires it
    // to sd_bus_set_method_call_timeout (a 0 per-call timeout resolves to the connection
    // default inside sd_bus_call); the JVM backend now consults the stored value the same way.
    // An explicit per-call timeout must still win over the connection default.
    @Test
    fun connectionMethodCallTimeout_appliesToCallsWithoutExplicitTimeout() = runBlocking {
        val ids = uniqueFixtureIds("connTimeout")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        proxyConnection.methodCallTimeout = 100.milliseconds
        val obj = createObject(serverConnection, ids.path)
        // Same slow-method fixture as methodCallTimeout_surfacesTimeoutError: the handler
        // sleeps past the (connection-default) timeout, then completes while both loops are
        // still up so its late reply doesn't hit a torn-down native connection.
        val serverDelayMs = 600L
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Slow")) {
                asyncCall { value: Int ->
                    delay(serverDelayMs)
                    value
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            // No per-call timeout anywhere in this call: the raw message API's
            // callMethod(message) overload carries the unset sentinel, so the connection's
            // methodCallTimeout default must apply and expire the call.
            val thrown = assertFailsWith<SdbusException> {
                val call = proxy.createMethodCall(ids.iface, MethodName("Slow"))
                call.append(1)
                proxy.callMethod(call)
            }
            assertTrue(
                thrown.name.contains("Timeout") ||
                    thrown.name.contains("NoReply") ||
                    thrown.errorMessage.contains("timed out", ignoreCase = true),
                "expected a timeout-shaped error, got name='${thrown.name}' " +
                    "message='${thrown.errorMessage}'"
            )

            // An explicit per-call timeout always wins over the connection default: with a
            // generous per-call timeout the same slow method completes successfully.
            val result = proxy.callMethod<Int>(ids.iface, MethodName("Slow")) {
                call(2)
                timeout = 5_000.milliseconds
            }
            assertEquals(2, result)

            // Let the first (timed-out) handler invocation flush its late reply while the
            // loops are still alive.
            delay(serverDelayMs + 400)
        } finally {
            withTimeout(5_000) {
                proxyConnection.stopEventLoop()
                serverConnection.stopEventLoop()
            }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // --- remote D-Bus error propagation ----------------------------------------------------

    // A handler that throws a named D-Bus SdbusException must reach the client with the same name AND
    // message on both backends. This pins the error model: the name is the contract, not an
    // implementation detail that one backend is free to rewrite.
    @Test
    fun remoteNamedError_propagatesNameAndMessage() = runBlocking {
        val ids = uniqueFixtureIds("namedError")
        val errorName = "org.freedesktop.DBus.Error.AccessDenied"
        val errorMessage = "A test error occurred"
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Throw")) {
                call<Int, Int> { _ ->
                    throw SdbusException(errorName, errorMessage)
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<SdbusException> {
                proxy.callMethod<Int>(ids.iface, MethodName("Throw")) {
                    call(1)
                }
            }
            assertEquals(errorName, thrown.name)
            assertEquals(errorMessage, thrown.errorMessage)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // A method that simply doesn't exist must produce an SdbusException (the daemon's UnknownMethod) on
    // both backends rather than silently returning or hanging.
    @Test
    fun callToNonexistentMethod_surfacesError() = runBlocking {
        val ids = uniqueFixtureIds("noMethod")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Present")) {
                call { value: Int -> value }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<SdbusException> {
                proxy.callMethod<Int>(ids.iface, MethodName("DoesNotExist")) {
                    call(1)
                }
            }
            assertTrue(thrown.name.isNotBlank(), "error should carry a name")
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // A handler that throws a plain (non-SdbusException) exception must surface the SAME, valid
    // D-Bus error name on both backends. Regression for the parity sweep (#141): toError() used to
    // put the exception's *message* in the error-name slot ("boom"), which is not a valid D-Bus
    // name — native then failed to send a proper error (client saw NoReply) while JVM passed the
    // bogus name through. Both must now report org.freedesktop.DBus.Error.Failed.
    @Test
    fun handlerThrowingRawException_reportsFailedErrorNameOnBothBackends() = runBlocking {
        val ids = uniqueFixtureIds("rawThrow")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Boom")) {
                call { _: Int -> if (alwaysThrows) throw RuntimeException("boom") else 0 }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<SdbusException> {
                proxy.callMethod<Int>(ids.iface, MethodName("Boom")) { call(1) }
            }
            assertEquals("org.freedesktop.DBus.Error.Failed", thrown.name)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // --- malformed / signature-mismatch handling -------------------------------------------

    // Calling a method with the wrong argument types (server expects an Int, client sends a
    // String) must be rejected as an error on both backends, not dispatched to the handler.
    @Test
    fun callWithWrongArgumentType_surfacesError() = runBlocking {
        val ids = uniqueFixtureIds("wrongArg")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("WantsInt")) {
                call { value: Int -> value }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            assertFailsWith<Throwable> {
                proxy.callMethod<Int>(ids.iface, MethodName("WantsInt")) {
                    call("not-an-int")
                }
            }
            Unit
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // --- connection teardown behavior ------------------------------------------------------

    // Tearing down a live proxy connection (stopEventLoop + release) after a successful call
    // must complete cleanly and consistently on both backends -- no crash, no hang. The teardown
    // is time-bounded so a hang in the known native-teardown path surfaces as a deterministic
    // test failure rather than an indefinitely stuck suite.
    //
    // This also asserts the sub-case that was scoped down when this suite landed (PR #54) and
    // restored by the #56 decision: a *subsequent* call on the released proxy connection must
    // throw on BOTH backends, never be silently serviced. The native backend guards every
    // post-release operation with require(!released) { "Connection has already been released" }
    // (ConnectionImpl.checkNotReleased); the JVM backend now guards its in-process dispatch
    // short circuit with the same exception type and message.
    @Test
    fun proxyConnectionTeardown_completesCleanly_andCallAfterReleaseFails() = runBlocking {
        val ids = uniqueFixtureIds("teardownClean")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Echo")) {
                call { value: Int -> value }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(
            proxyConnection,
            ids.service,
            ids.path,
            runEventLoopThread = false
        )

        try {
            // Sanity: the call works before teardown.
            assertEquals(
                5,
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) { call(5) }
            )

            // Teardown itself must be clean and bounded on both backends.
            withTimeout(5_000) {
                proxyConnection.stopEventLoop()
            }
            proxyConnection.release()

            // A call after the proxy's connection has been released must throw on both
            // backends, not be serviced. The per-call timeout bounds the failure mode where
            // a regressed backend would instead try to perform the call.
            val thrown = assertFailsWith<IllegalArgumentException> {
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) {
                    call(6)
                    timeout = 2_000.milliseconds
                }
            }
            assertEquals("Connection has already been released", thrown.message)
            proxy.release()
        } finally {
            withTimeout(5_000) {
                serverConnection.stopEventLoop()
            }
            registration.release()
            obj.release()
            serverConnection.release()
        }
    }

    // Tearing down the server side while the client still holds a proxy must not crash the
    // client: a call to the now-gone service surfaces as an SdbusException on both backends. Time-bounded
    // to keep a teardown hang from stalling the suite.
    @Test
    fun callAfterServerTeardown_failsConsistently() = runBlocking {
        val ids = uniqueFixtureIds("teardownServer")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Echo")) {
                call { value: Int -> value }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            assertEquals(
                9,
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) { call(9) }
            )

            // Tear the server down.
            withTimeout(5_000) {
                serverConnection.stopEventLoop()
            }
            registration.release()
            obj.release()
            serverConnection.release()

            // The service no longer exists; the client call must surface an SdbusException, not hang.
            withTimeout(5_000) {
                assertFailsWith<SdbusException> {
                    proxy.callMethod<Int>(ids.iface, MethodName("Echo")) {
                        call(10)
                        timeout = 2_000.milliseconds
                    }
                }
            }
            Unit
        } finally {
            withTimeout(5_000) {
                proxyConnection.stopEventLoop()
            }
            proxy.release()
            proxyConnection.release()
        }
    }
}
