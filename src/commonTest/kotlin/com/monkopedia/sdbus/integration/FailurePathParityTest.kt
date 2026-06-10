package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
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
 * Error / failure-path consistency tests. These assert that BOTH backends (the native sd-bus
 * backend exercised by the linux*Test targets and the pure-JVM backend exercised by jvmTest)
 * surface failures the same way: a timeout becomes a timeout [Error], a server-thrown named
 * D-Bus error round-trips its name + message, a wrong-argument-type / signature mismatch becomes
 * an [Error] rather than reaching the handler, and tearing a connection down stays consistent
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

    // --- method-call timeout ---------------------------------------------------------------

    // A server method that deliberately sleeps far past the client's per-call timeout must
    // surface as a timeout Error on both backends -- not a hang, and not some other error type.
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
                acall { value: Int ->
                    delay(serverDelayMs)
                    value
                }
            }
        }
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = withTimeout(3_000) {
                assertFailsWith<Error> {
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
                proxyConnection.leaveEventLoop()
                serverConnection.leaveEventLoop()
            }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // --- remote D-Bus error propagation ----------------------------------------------------

    // A handler that throws a named D-Bus Error must reach the client with the same name AND
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
                    throw Error(errorName, errorMessage)
                }
            }
        }
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<Error> {
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

    // A method that simply doesn't exist must produce an Error (the daemon's UnknownMethod) on
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<Error> {
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
        serverConnection.enterEventLoopAsync()
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

    // Tearing down a live proxy connection (leaveEventLoop + release) after a successful call
    // must complete cleanly and consistently on both backends -- no crash, no hang. The teardown
    // is time-bounded so a hang in the known native-teardown path surfaces as a deterministic
    // test failure rather than an indefinitely stuck suite.
    //
    // NOTE: this intentionally does NOT assert that a *subsequent* call on the released proxy
    // throws. The two backends diverge there (the native backend rejects the call; the pure-JVM
    // backend currently still services it), so a "call-after-release fails" assertion is not a
    // parity property today -- see the PR description.
    @Test
    fun proxyConnectionTeardown_completesCleanly() = runBlocking {
        val ids = uniqueFixtureIds("teardownClean")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Echo")) {
                call { value: Int -> value }
            }
        }
        serverConnection.enterEventLoopAsync()
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
                proxyConnection.leaveEventLoop()
            }
            proxy.release()
            proxyConnection.release()
        } finally {
            withTimeout(5_000) {
                serverConnection.leaveEventLoop()
            }
            registration.release()
            obj.release()
            serverConnection.release()
        }
    }

    // Tearing down the server side while the client still holds a proxy must not crash the
    // client: a call to the now-gone service surfaces as an Error on both backends. Time-bounded
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            assertEquals(
                9,
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) { call(9) }
            )

            // Tear the server down.
            withTimeout(5_000) {
                serverConnection.leaveEventLoop()
            }
            registration.release()
            obj.release()
            serverConnection.release()

            // The service no longer exists; the client call must surface an Error, not hang.
            withTimeout(5_000) {
                assertFailsWith<Error> {
                    proxy.callMethod<Int>(ids.iface, MethodName("Echo")) {
                        call(10)
                        timeout = 2_000.milliseconds
                    }
                }
            }
            Unit
        } finally {
            withTimeout(5_000) {
                proxyConnection.leaveEventLoop()
            }
            proxy.release()
            proxyConnection.release()
        }
    }
}
