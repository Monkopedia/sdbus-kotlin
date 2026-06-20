package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.RequestNameFlag
import com.monkopedia.sdbus.RequestNameReply
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.getAllProperties
import com.monkopedia.sdbus.getAllPropertiesAsync
import com.monkopedia.sdbus.getProperty
import com.monkopedia.sdbus.getPropertyAsync
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.setPropertyAsync
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

class CommonApiIntegrationTest {
    @Serializable
    private data class Point(val x: Int, val y: Int)

    // Backs the generated-adaptor `with(::prop)` binding exercised by the #89 regression test.
    private class PropertyHolder {
        var value: Int = 0
    }

    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.common.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/common/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    @Test
    fun typedMethodCall_roundTripsThroughProxyAndObject() {
        val ids = uniqueFixtureIds("typed")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Add")) {
                call { a: Int, b: Int -> a + b }
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
            val result = proxy.callMethod<Int>(ids.iface, MethodName("Add")) {
                call(7, 8)
            }

            assertEquals(15, result)
        } finally {
            runBlocking {
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

    // Regression for the BlueZ-on-JVM failure: a method taking an empty a{sv} (e.g. the
    // GATT ReadValue `options` dict) must serialize with the declared signature "a{sv}".
    // The JVM backend used to infer the signature from runtime values, and an empty map
    // has no entries to infer from — it emitted the malformed "a{}", which made the bus
    // daemon drop the connection ("Underlying transport returned -1"). Passes on native;
    // reproduced the failure on JVM before the fix.
    @Test
    fun typedMethodCall_withEmptyDictArg_roundTrips() {
        val ids = uniqueFixtureIds("emptyDict")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("CountOptions")) {
                call { options: Map<String, Variant> -> options.size }
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
            val result = proxy.callMethod<Int>(ids.iface, MethodName("CountOptions")) {
                call(emptyMap<String, Variant>())
            }
            assertEquals(0, result)
        } finally {
            runBlocking {
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

    // Sibling of the empty-dict case: an empty array (`as`) has no element to infer from,
    // so the JVM backend used to emit the malformed bare "a". Verifies the declared
    // signature is used instead.
    @Test
    fun typedMethodCall_withEmptyArrayArg_roundTrips() {
        val ids = uniqueFixtureIds("emptyArray")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("CountItems")) {
                call { items: List<String> -> items.size }
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
            val result = proxy.callMethod<Int>(ids.iface, MethodName("CountItems")) {
                call(emptyList<String>())
            }
            assertEquals(0, result)
        } finally {
            runBlocking {
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

    @Test
    fun createMethodCall_carriesInterfaceAndMethodMetadata() {
        val ids = uniqueFixtureIds("metadata")
        val connection = createBusConnection()
        val proxy = createProxy(
            connection,
            ids.service,
            ids.path,
            runEventLoopThread = false
        )

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("Ping"))
            assertEquals(ids.iface, call.interfaceName)
            assertEquals("Ping", call.memberName?.value)
            assertEquals(ids.path, call.objectPath)
        } finally {
            proxy.release()
            connection.release()
        }
    }

    @Test
    fun releasedVTable_rejectsFurtherMethodCalls() {
        val ids = uniqueFixtureIds("release")
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
                42,
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) {
                    call(42)
                }
            )

            registration.release()

            assertFailsWith<SdbusException> {
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) {
                    call(42)
                }
            }
        } finally {
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun callbackAsyncMethodCall_deliversReplyAndCompletes() = runBlocking {
        val ids = uniqueFixtureIds("asyncCallback")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Multiply")) {
                call { a: Int, b: Int -> a * b }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackValue = CompletableDeferred<Int>()
        val callbackError = CompletableDeferred<SdbusException?>()

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("Multiply"))
            call.append(6)
            call.append(7)

            val pending = proxy.callMethodAsync(call) { reply, error ->
                callbackError.complete(error)
                if (error == null) {
                    callbackValue.complete(reply.readIntReply())
                }
            }

            assertNull(withTimeout(2_000) { callbackError.await() })
            assertEquals(42, withTimeout(2_000) { callbackValue.await() })
            awaitPendingCompletion(pending)
            assertFalse(pending.isPending())
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun suspendAsyncTypedMethodCall_roundTripsValue() = runBlocking {
        val ids = uniqueFixtureIds("asyncSuspend")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Add")) {
                call { a: Int, b: Int -> a + b }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val result = proxy.callMethodAsync<Int>(ids.iface, MethodName("Add")) {
                call(10, 5)
            }

            assertEquals(15, result)
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun methodCallSend_roundTripsValue() {
        val ids = uniqueFixtureIds("methodSend")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Add")) {
                call { a: Int, b: Int -> a + b }
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
            val call = proxy.createMethodCall(ids.iface, MethodName("Add"))
            call.append(20)
            call.append(22)

            val reply = call.send(2.seconds)
            assertEquals(42, reply.readIntReply())
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun typedMethodCall_propagatesHandlerErrors() {
        val ids = uniqueFixtureIds("typedError")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Fail")) {
                call<Int, Int> { _ ->
                    error("boom")
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<SdbusException> {
                proxy.callMethod<Int>(ids.iface, MethodName("Fail")) {
                    call(1)
                }
            }
            assertTrue(thrown.name.isNotBlank())
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun typedMethodCall_roundTripsCollectionsAndDataClasses() {
        val ids = uniqueFixtureIds("typedComplex")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Build")) {
                call { base: Int, items: List<Int> ->
                    Point(base + items.sum(), items.size)
                }
            }
            method(MethodName("Keys")) {
                call { map: Map<String, Int> ->
                    map.keys.sorted()
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val built = proxy.callMethod<Point>(ids.iface, MethodName("Build")) {
                call(5, listOf(1, 2, 3))
            }
            assertEquals(Point(11, 3), built)

            val keys = proxy.callMethod<List<String>>(ids.iface, MethodName("Keys")) {
                call(mapOf("b" to 2, "a" to 1))
            }
            assertEquals(listOf("a", "b"), keys)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun callbackAsyncMethodCall_reportsErrors() = runBlocking {
        val ids = uniqueFixtureIds("asyncError")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Fail")) {
                call<Int, Int> { _ ->
                    error("broken")
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackError = CompletableDeferred<SdbusException?>()

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("Fail"))
            call.append(1)

            val pending = proxy.callMethodAsync(call) { _, error ->
                callbackError.complete(error)
            }

            val error = withTimeout(2_000) { callbackError.await() }
            assertTrue(error != null)
            awaitPendingCompletion(pending)
            assertFalse(pending.isPending())
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun callbackAsyncMethodCall_timeoutReportsError() = runBlocking {
        val ids = uniqueFixtureIds("asyncTimeout")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("SlowAdd")) {
                asyncCall { a: Int, b: Int ->
                    delay(500)
                    a + b
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackError = CompletableDeferred<SdbusException?>()

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("SlowAdd"))
            call.append(1)
            call.append(2)
            val pending = proxy.callMethodAsync(
                call,
                asyncReplyCallback = { _, error -> callbackError.complete(error) },
                timeout = 100_000u
            )

            val error = withTimeout(2_000) { callbackError.await() }
            assertTrue(error != null)
            awaitPendingCompletion(pending)
            assertFalse(pending.isPending())
            pending.release()
            delay(600)
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun callbackAsyncMethodCall_timeoutFailsQuickly() = runBlocking {
        val ids = uniqueFixtureIds("asyncTimeoutQuick")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("SlowAdd")) {
                asyncCall { a: Int, b: Int ->
                    delay(1_000)
                    a + b
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackError = CompletableDeferred<SdbusException?>()

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("SlowAdd"))
            call.append(1)
            call.append(2)
            val pending = proxy.callMethodAsync(
                call,
                asyncReplyCallback = { _, error -> callbackError.complete(error) },
                timeout = 1_000u
            )

            val error = withTimeout(500) { callbackError.await() }
            assertTrue(error != null)
            assertTrue(
                error.name.contains("Timeout") ||
                    error.name.contains("NoReply") ||
                    error.errorMessage.contains("timed out")
            )
            pending.release()
            delay(1_100)
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun callbackAsyncMethodCall_isPendingUntilCallbackCompletes() = runBlocking {
        val ids = uniqueFixtureIds("asyncPending")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("SlowAdd")) {
                asyncCall { a: Int, b: Int ->
                    delay(250)
                    a + b
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackValue = CompletableDeferred<Int>()

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("SlowAdd"))
            call.append(20)
            call.append(22)
            val pending = proxy.callMethodAsync(call) { reply, error ->
                if (error == null) {
                    callbackValue.complete(reply.readIntReply())
                }
            }
            assertTrue(pending.isPending())
            assertEquals(42, withTimeout(2_000) { callbackValue.await() })
            awaitPendingCompletion(pending)
            assertFalse(pending.isPending())
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun callbackAsyncMethodCall_releasePreventsCallbackDelivery() = runBlocking {
        val ids = uniqueFixtureIds("asyncCancel")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("SlowAdd")) {
                asyncCall { a: Int, b: Int ->
                    delay(250)
                    a + b
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackSeen = CompletableDeferred<Unit>()

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("SlowAdd"))
            call.append(20)
            call.append(22)

            val pending = proxy.callMethodAsync(call) { _, _ ->
                callbackSeen.complete(Unit)
            }

            pending.release()
            pending.release()

            assertNull(withTimeoutOrNull(600) { callbackSeen.await() })
            awaitPendingCompletion(pending)
            assertFalse(pending.isPending())
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun suspendAsyncTypedMethodCall_propagatesErrors() = runBlocking {
        val ids = uniqueFixtureIds("asyncSuspendError")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Fail")) {
                call<Int, Int> { _ ->
                    error("broken")
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            assertFailsWith<SdbusException> {
                proxy.callMethodAsync<Int>(ids.iface, MethodName("Fail")) {
                    call(1)
                }
            }
            Unit
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun suspendAsyncTypedMethodCall_handlesParallelInvocations() = runBlocking {
        val ids = uniqueFixtureIds("asyncParallel")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("WaitAndEcho")) {
                asyncCall { value: Int ->
                    delay(value.toLong())
                    value
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val jobs = listOf(150, 100, 50).map { value ->
                async {
                    proxy.callMethodAsync<Int>(ids.iface, MethodName("WaitAndEcho")) {
                        call(value)
                    }
                }
            }
            val results = jobs.map { withTimeout(2_000) { it.await() } }.toSet()
            assertEquals(setOf(50, 100, 150), results)
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun suspendAsyncTypedMethodCall_handlesBulkParallelInvocations() = runBlocking {
        val ids = uniqueFixtureIds("asyncBulkParallel")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Echo")) {
                asyncCall { value: Int -> value }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val workers = List(3) {
                async(Dispatchers.Default) {
                    var correct = 0
                    repeat(100) { i ->
                        val expected = i % 2
                        val result = proxy.callMethodAsync<Int>(ids.iface, MethodName("Echo")) {
                            call(expected)
                        }
                        if (result == expected) {
                            correct += 1
                        }
                    }
                    correct
                }
            }
            val total = workers.sumOf { withTimeout(5_000) { it.await() } }
            assertEquals(300, total)
        } finally {
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalRoundTrip_deliversPayloadToRegisteredProxyHandler() = runBlocking {
        val ids = uniqueFixtureIds("signal")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<String>()
        val signalRegistration = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
            it.rewind(false)
            seen.complete(it.readString())
        }

        try {
            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("hello")
            }

            assertEquals("hello", withTimeout(2_000) { seen.await() })
        } finally {
            signalRegistration.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalRoundTrip_createSignalSendDeliversPayloadToRegisteredProxyHandler() = runBlocking {
        val ids = uniqueFixtureIds("signalSend")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<String>()
        val signalRegistration = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
            it.rewind(false)
            seen.complete(it.readString())
        }

        try {
            val signal = obj.createSignal(ids.iface, SignalName("Changed"))
            signal.append("hello-send")
            signal.send()

            assertEquals("hello-send", withTimeout(2_000) { seen.await() })
        } finally {
            signalRegistration.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalHandler_exposesCurrentlyProcessedMessageOnProxy() = runBlocking {
        val ids = uniqueFixtureIds("signalCurrentMessage")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val memberSeen = CompletableDeferred<String>()
        val pathSeen = CompletableDeferred<String>()
        val signalRegistration = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
            val current = proxy.currentlyProcessedMessage
            memberSeen.complete(current.memberName?.value.orEmpty())
            pathSeen.complete(current.objectPath?.value.orEmpty())
        }

        try {
            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("hello")
            }
            assertEquals("Changed", withTimeout(2_000) { memberSeen.await() })
            assertEquals(ids.path.value, withTimeout(2_000) { pathSeen.await() })
        } finally {
            signalRegistration.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalHandlers_deliverToMultipleProxiesAndStopAfterRelease() = runBlocking {
        val ids = uniqueFixtureIds("signalMulti")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnectionA = createBusConnection()
        val proxyConnectionB = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnectionA.startEventLoop()
        proxyConnectionB.startEventLoop()
        val proxyA = createProxy(proxyConnectionA, ids.service, ids.path)
        val proxyB = createProxy(proxyConnectionB, ids.service, ids.path)
        val seenA = CompletableDeferred<Unit>()
        val seenB = CompletableDeferred<Unit>()
        var countA = 0
        var countB = 0
        val handlerA = proxyA.registerSignalHandler(ids.iface, SignalName("Changed")) {
            countA += 1
            seenA.complete(Unit)
        }
        val handlerB = proxyB.registerSignalHandler(ids.iface, SignalName("Changed")) {
            countB += 1
            seenB.complete(Unit)
        }

        try {
            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("first")
            }
            withTimeout(2_000) { seenA.await() }
            withTimeout(2_000) { seenB.await() }
            assertEquals(1, countA)
            assertEquals(1, countB)

            handlerA.release()

            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("second")
            }
            delay(250)
            assertEquals(1, countA)
            assertEquals(2, countB)
        } finally {
            handlerB.release()
            registration.release()
            proxyA.release()
            proxyB.release()
            obj.release()
            proxyConnectionA.stopEventLoop()
            proxyConnectionB.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnectionA.release()
            proxyConnectionB.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalHandler_canBeReRegisteredAfterRelease() = runBlocking {
        val ids = uniqueFixtureIds("signalReregister")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        var firstCount = 0
        val firstHandler = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
            firstCount += 1
        }

        try {
            firstHandler.release()
            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("suppressed")
            }
            delay(250)
            assertEquals(0, firstCount)

            val seen = CompletableDeferred<Unit>()
            var secondCount = 0
            val secondHandler = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
                secondCount += 1
                seen.complete(Unit)
            }
            try {
                obj.emitSignal(ids.iface, SignalName("Changed")) {
                    call("live")
                }
                withTimeout(2_000) { seen.await() }
                assertEquals(1, secondCount)
            } finally {
                secondHandler.release()
            }
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun proxySignalHandler_ignoresSignalsFromOtherServiceName() = runBlocking {
        val ids = uniqueFixtureIds("signalFilter")
        val otherService = ServiceName("${ids.service.value}.other")
        val serverConnection = createBusConnection(ids.service)
        val otherConnection = createBusConnection(otherService)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val otherObj = createObject(otherConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        val otherRegistration = otherObj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        otherConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<Unit>()
        val handler = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
            seen.complete(Unit)
        }

        try {
            otherObj.emitSignal(ids.iface, SignalName("Changed")) {
                call("foreign")
            }
            assertNull(withTimeoutOrNull(600) { seen.await() })
        } finally {
            handler.release()
            otherRegistration.release()
            registration.release()
            proxy.release()
            otherObj.release()
            obj.release()
            proxyConnection.stopEventLoop()
            otherConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            otherConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun propertiesChangedSignal_roundTripsForDeclaredProperty() = runBlocking {
        val ids = uniqueFixtureIds("properties")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val stateProperty = PropertyName("state")
        var state = false
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(stateProperty) {
                withGetter { state }
                withSetter<Boolean> { value -> state = value }
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<Pair<Map<PropertyName, Variant>, List<PropertyName>>>()
        val signalRegistration = proxy.onSignal(
            InterfaceName("org.freedesktop.DBus.Properties"),
            SignalName("PropertiesChanged")
        ) {
            call {
                    changedInterface: InterfaceName,
                    changedProperties: Map<PropertyName, Variant>,
                    invalidatedProperties: List<PropertyName>
                ->
                if (changedInterface == ids.iface) {
                    seen.complete(changedProperties to invalidatedProperties)
                }
            }
        }

        try {
            state = true
            obj.emitPropertiesChangedSignal(ids.iface, listOf(stateProperty))

            val (changedProperties, invalidatedProperties) = withTimeout(2_000) { seen.await() }
            // Matching native, the emitted signal carries the current value in
            // changed_properties (read from the registered getter), not invalidated_properties.
            // This is what makes a consumer's PropertyGetter.changes() fire, not just
            // changesOrNull().
            assertTrue(changedProperties.containsKey(stateProperty))
            assertFalse(invalidatedProperties.contains(stateProperty))
            assertTrue(changedProperties.getValue(stateProperty).get<Boolean>())
        } finally {
            signalRegistration.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun connectionApi_basicsAreCallable() {
        val ids = uniqueFixtureIds("connection")
        val connection = createBusConnection()
        val ownName = ServiceName("${ids.service.value}.Owned")
        val objectManager = connection.addObjectManager(ids.path)
        val match = connection.addMatch(
            "type='signal',path='${ids.path.value}',interface='${ids.iface.value}',member='Any'"
        ) { }

        try {
            assertTrue(connection.uniqueName.value.isNotBlank())
            connection.methodCallTimeout = 1500.milliseconds
            assertTrue(connection.methodCallTimeout >= kotlin.time.Duration.ZERO)
            connection.requestName(ownName)
            connection.releaseName(ownName)
        } finally {
            match.release()
            objectManager.release()
            connection.release()
        }
    }

    // Coverage for #112: requestName now reports a RequestNameReply for each outcome. This lives in
    // commonTest, so the IDENTICAL assertions run on BOTH backends — native sd-bus (linuxX64Test)
    // and the JVM wire backend (jvmTest). Each platform asserting the same expected enum for every
    // scenario IS the cross-backend-consistency regression guard for the 0.6.0 fix that made the two
    // backends agree on these reply codes.
    @Test
    fun requestName_reportsConsistentReplyCodesAcrossBackends() {
        val ids = uniqueFixtureIds("requestName")
        val name = ServiceName("${ids.service.value}.Owned")
        val owner = createBusConnection()
        val queued = createBusConnection()
        val rejected = createBusConnection()

        try {
            // A fresh, unowned name → the caller becomes the primary owner.
            assertEquals(RequestNameReply.PRIMARY_OWNER, owner.requestName(name))

            // The SAME connection re-requesting its own name → already the owner.
            assertEquals(RequestNameReply.ALREADY_OWNER, owner.requestName(name))

            // A SECOND connection, no flags → queued behind the current owner (default queueing).
            assertEquals(RequestNameReply.IN_QUEUE, queued.requestName(name))

            // A different connection with DO_NOT_QUEUE → refused outright rather than queued.
            assertEquals(
                RequestNameReply.EXISTS,
                rejected.requestName(name, RequestNameFlag.DO_NOT_QUEUE)
            )
        } finally {
            runCatching { owner.releaseName(name) }
            runCatching { queued.releaseName(name) }
            owner.release()
            queued.release()
            rejected.release()
        }
    }

    // Coverage for #110: the direct two-arg property accessors on Proxy
    // (getPropertyAsync / setPropertyAsync / getAllProperties / getAllPropertiesAsync). Confirms the
    // async variants agree with the sync getProperty/setProperty results and that the Variant unwrap
    // yields the typed value. Runs on both backends via commonTest.
    @Test
    fun directPropertyAccessors_matchSyncResultsAndUnwrapVariant() = runBlocking {
        val ids = uniqueFixtureIds("directProps")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val levelProperty = PropertyName("level")
        var level = 3
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(levelProperty) {
                withGetter { level }
                withSetter<Int> { value -> level = value }
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            // Async get matches sync get, and both unwrap the Variant to the typed value.
            val sync = proxy.getProperty<Int>(ids.iface, levelProperty)
            val async = proxy.getPropertyAsync<Int>(ids.iface, levelProperty)
            assertEquals(3, sync)
            assertEquals(sync, async)

            // Async set round-trips; subsequent sync and async reads both reflect the new value.
            proxy.setPropertyAsync(ids.iface, levelProperty, 11)
            assertEquals(11, level)
            assertEquals(11, proxy.getProperty<Int>(ids.iface, levelProperty))
            assertEquals(11, proxy.getPropertyAsync<Int>(ids.iface, levelProperty))

            // getAllProperties (sync) and getAllPropertiesAsync agree, with correct Variant unwrap.
            val all = proxy.getAllProperties(ids.iface)
            val allAsync = proxy.getAllPropertiesAsync(ids.iface)
            assertEquals(11, all[levelProperty]?.get<Int>())
            assertEquals(11, allAsync[levelProperty]?.get<Int>())
            assertEquals(
                all.mapValues { it.value.get<Int>() },
                allAsync.mapValues { it.value.get<Int>() }
            )
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun addMatch_receivesMatchingSignalAndStopsAfterRelease() = runBlocking {
        val ids = uniqueFixtureIds("match")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val seen = CompletableDeferred<Unit>()
        var callbackCount = 0
        val match = proxyConnection.addMatch(
            "path='${ids.path.value}',interface='${ids.iface.value}',member='Changed'"
        ) { message ->
            if (message.objectPath == ids.path && message.memberName?.value == "Changed") {
                callbackCount += 1
                seen.complete(Unit)
            }
        }

        try {
            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("first")
            }
            withTimeout(2_000) { seen.await() }

            match.release()

            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("second")
            }
            delay(200)
            assertEquals(1, callbackCount)
        } finally {
            registration.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun propertiesProxy_supportsGetSetAndGetAll() = runBlocking {
        val ids = uniqueFixtureIds("propertiesProxy")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val stateProperty = PropertyName("state")
        var state = false
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(stateProperty) {
                withGetter { state }
                withSetter<Boolean> { value -> state = value }
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val propertiesProxy = PropertiesProxy(proxy)

        try {
            PropertiesProxy.run {
                propertiesProxy.set(ids.iface, stateProperty, true)
                assertEquals(true, propertiesProxy.get<Boolean>(ids.iface, stateProperty))
                assertEquals(
                    true,
                    propertiesProxy.getAsync<Boolean>(ids.iface, stateProperty)
                )
            }
            val all = propertiesProxy.getAll(ids.iface)
            assertTrue(stateProperty in all)
            assertTrue(all[stateProperty]?.let { it.get<Boolean>() } == true)

            propertiesProxy.setAsync(ids.iface, stateProperty, Variant(false))
            val allAsync = propertiesProxy.getAllAsync(ids.iface)
            assertTrue(stateProperty in allAsync)
            assertTrue(allAsync[stateProperty]?.let { it.get<Boolean>() } == false)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // Regression for #89: the generated-adaptor property binding path. AdaptorGenerator emits
    // `with(this@Adaptor::<prop>)` for every property, and `with(KProperty0)` used to discard the
    // value returned by the getter (`getter = { receiver.get() }`) instead of serializing it into
    // the reply. That produced an empty Properties.Get/GetAll reply on the wire, breaking property
    // reads for every codegen adaptor on native. This test binds via `with(::prop)` (NOT
    // withGetter/withSetter) and performs a real remote Get/GetAll, asserting the real value comes
    // back. It fails on main (empty/wrong value) and passes once the getter serializes.
    @Test
    fun propertiesProxy_supportsGetAndGetAllViaKotlinPropertyBinding() = runBlocking {
        val ids = uniqueFixtureIds("propertiesKProp")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val countProperty = PropertyName("count")
        val holder = PropertyHolder()
        holder.value = 42
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(countProperty) {
                with(holder::value)
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val propertiesProxy = PropertiesProxy(proxy)

        try {
            // Remote Properties.Get must return the real backing value, not an empty reply.
            assertEquals(
                42,
                PropertiesProxy.run { propertiesProxy.get<Int>(ids.iface, countProperty) }
            )

            // GetAll must include the property with its real value.
            val all = propertiesProxy.getAll(ids.iface)
            assertTrue(countProperty in all)
            assertEquals(42, all[countProperty]?.get<Int>())

            // The mutable binding (KMutableProperty0) round-trips a Set back into the property.
            PropertiesProxy.run {
                propertiesProxy.set(ids.iface, countProperty, 7)
            }
            assertEquals(7, holder.value)
            assertEquals(
                7,
                PropertiesProxy.run { propertiesProxy.get<Int>(ids.iface, countProperty) }
            )
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun propertiesProxy_setFailsForReadOnlyProperty() = runBlocking {
        val ids = uniqueFixtureIds("propertiesReadOnly")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val stateProperty = PropertyName("state")
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(stateProperty) {
                withGetter { true }
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val propertiesProxy = PropertiesProxy(proxy)

        try {
            val thrown = assertFailsWith<SdbusException> {
                PropertiesProxy.run {
                    propertiesProxy.set(ids.iface, stateProperty, false)
                }
            }
            assertFalse(thrown.name.isBlank())
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun propertySetter_exposesCurrentlyProcessedMessageOnObject() = runBlocking {
        val ids = uniqueFixtureIds("propertyCurrentMessage")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val stateProperty = PropertyName("state")
        var state = false
        val memberSeen = CompletableDeferred<String>()
        val senderSeen = CompletableDeferred<String>()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(stateProperty) {
                withGetter { state }
                withSetter<Boolean> { value ->
                    state = value
                    val current = obj.currentlyProcessedMessage
                    memberSeen.complete(current.memberName?.value.orEmpty())
                    senderSeen.complete(current.sender?.value.orEmpty())
                }
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val propertiesProxy = PropertiesProxy(proxy)

        try {
            PropertiesProxy.run {
                propertiesProxy.set(ids.iface, stateProperty, true)
            }
            assertEquals("Set", withTimeout(2_000) { memberSeen.await() })
            assertTrue(withTimeout(2_000) { senderSeen.await() }.isNotBlank())
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun interfacesAddedAndRemovedSignals_roundTripForExplicitList() = runBlocking {
        val ids = uniqueFixtureIds("interfaces")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val managerPath = ObjectPath("/com/monkopedia/sdbus/common")
        val managerObj = createObject(serverConnection, managerPath)
        val manager = managerObj.addObjectManager()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Ping")) {
                call<Unit> { Unit }
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, managerPath)
        val seenMembers = mutableListOf<String>()
        val added = CompletableDeferred<Unit>()
        val removed = CompletableDeferred<Unit>()
        val addedRegistration = proxy.registerSignalHandler(
            InterfaceName("org.freedesktop.DBus.ObjectManager"),
            SignalName("InterfacesAdded")
        ) {
            seenMembers += it.memberName?.value.orEmpty()
            added.complete(Unit)
        }
        val removedRegistration = proxy.registerSignalHandler(
            InterfaceName("org.freedesktop.DBus.ObjectManager"),
            SignalName("InterfacesRemoved")
        ) {
            seenMembers += it.memberName?.value.orEmpty()
            removed.complete(Unit)
        }

        try {
            obj.emitInterfacesAddedSignal(listOf(ids.iface))
            obj.emitInterfacesRemovedSignal(listOf(ids.iface))
            withTimeout(2_000) { added.await() }
            withTimeout(2_000) { removed.await() }
            assertTrue("InterfacesAdded" in seenMembers)
            assertTrue("InterfacesRemoved" in seenMembers)
        } finally {
            removedRegistration.release()
            addedRegistration.release()
            registration.release()
            manager.release()
            managerObj.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    private fun Message.readIntReply(): Int {
        rewind(false)
        return readInt()
    }

    private suspend fun awaitPendingCompletion(pending: PendingAsyncCall, timeoutMs: Long = 2_000) {
        withTimeout(timeoutMs) {
            while (pending.isPending()) {
                delay(10)
            }
        }
    }
}
