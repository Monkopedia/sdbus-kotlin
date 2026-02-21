package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
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
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(
            proxyConnection,
            ids.service,
            ids.path,
            dontRunEventLoopThread = true
        )

        try {
            val result = proxy.callMethod<Int>(ids.iface, MethodName("Add")) {
                call(7, 8)
            }

            assertEquals(15, result)
        } finally {
            runBlocking {
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

    @Test
    fun createMethodCall_carriesInterfaceAndMethodMetadata() {
        val ids = uniqueFixtureIds("metadata")
        val connection = createBusConnection()
        val proxy = createProxy(
            connection,
            ids.service,
            ids.path,
            dontRunEventLoopThread = true
        )

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("Ping"))
            assertEquals(ids.iface.value, call.getInterfaceName())
            assertEquals("Ping", call.getMemberName())
            assertEquals(ids.path.value, call.getPath())
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            assertEquals(
                42,
                proxy.callMethod<Int>(ids.iface, MethodName("Echo")) {
                    call(42)
                }
            )

            registration.release()

            assertFailsWith<Error> {
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackValue = CompletableDeferred<Int>()
        val callbackError = CompletableDeferred<Error?>()

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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val result = proxy.callMethodAsync<Int>(ids.iface, MethodName("Add")) {
                call(10, 5)
            }

            assertEquals(15, result)
        } finally {
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(
            proxyConnection,
            ids.service,
            ids.path,
            dontRunEventLoopThread = true
        )

        try {
            val call = proxy.createMethodCall(ids.iface, MethodName("Add"))
            call.append(20)
            call.append(22)

            val reply = call.send(2_000_000u)
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val thrown = assertFailsWith<Error> {
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
        serverConnection.enterEventLoopAsync()
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackError = CompletableDeferred<Error?>()

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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
                acall { a: Int, b: Int ->
                    delay(500)
                    a + b
                }
            }
        }
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackError = CompletableDeferred<Error?>()

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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
                acall { a: Int, b: Int ->
                    delay(1_000)
                    a + b
                }
            }
        }
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val callbackError = CompletableDeferred<Error?>()

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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
                acall { a: Int, b: Int ->
                    delay(250)
                    a + b
                }
            }
        }
        serverConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
                acall { a: Int, b: Int ->
                    delay(250)
                    a + b
                }
            }
        }
        serverConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            assertFailsWith<Error> {
                proxy.callMethodAsync<Int>(ids.iface, MethodName("Fail")) {
                    call(1)
                }
            }
            Unit
        } finally {
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
                acall { value: Int ->
                    delay(value.toLong())
                    value
                }
            }
        }
        serverConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
                acall { value: Int -> value }
            }
        }
        serverConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val memberSeen = CompletableDeferred<String>()
        val pathSeen = CompletableDeferred<String>()
        val signalRegistration = proxy.registerSignalHandler(ids.iface, SignalName("Changed")) {
            val current = proxy.currentlyProcessedMessage
            memberSeen.complete(current.getMemberName().orEmpty())
            pathSeen.complete(current.getPath().orEmpty())
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnectionA.enterEventLoopAsync()
        proxyConnectionB.enterEventLoopAsync()
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
            proxyConnectionA.leaveEventLoop()
            proxyConnectionB.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        otherConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            otherConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            obj.emitPropertiesChangedSignal(ids.iface, listOf(stateProperty))

            val (changedProperties, invalidatedProperties) = withTimeout(2_000) { seen.await() }
            val propertySeen = changedProperties.containsKey(stateProperty) ||
                invalidatedProperties.contains(stateProperty)
            assertTrue(propertySeen)
        } finally {
            signalRegistration.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
            assertTrue(connection.getUniqueName().value.isNotBlank())
            connection.setMethodCallTimeout(1500.milliseconds)
            assertTrue(connection.getMethodCallTimeout() >= kotlin.time.Duration.ZERO)
            connection.requestName(ownName)
            connection.releaseName(ownName)
            connection.addMatchAsync(
                "type='signal',interface='${ids.iface.value}',member='Any'",
                callback = {},
                installCallback = {}
            ).release()
        } finally {
            match.release()
            objectManager.release()
            connection.release()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val seen = CompletableDeferred<Unit>()
        var callbackCount = 0
        val match = proxyConnection.addMatch(
            "path='${ids.path.value}',interface='${ids.iface.value}',member='Changed'"
        ) { message ->
            if (message.getPath() == ids.path.value && message.getMemberName() == "Changed") {
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun addMatchAsync_invokesInstallCallbackAndReceivesSignal() = runBlocking {
        val ids = uniqueFixtureIds("matchAsync")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Changed")) {
                with<String>("value")
            }
        }
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val installSeen = CompletableDeferred<Unit>()
        val signalSeen = CompletableDeferred<Unit>()
        val match = proxyConnection.addMatchAsync(
            "path='${ids.path.value}',interface='${ids.iface.value}',member='Changed'",
            callback = { signalSeen.complete(Unit) },
            installCallback = { installSeen.complete(Unit) }
        )

        try {
            withTimeout(2_000) { installSeen.await() }
            obj.emitSignal(ids.iface, SignalName("Changed")) {
                call("async")
            }
            withTimeout(2_000) { signalSeen.await() }
        } finally {
            match.release()
            registration.release()
            obj.release()
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val propertiesProxy = PropertiesProxy(proxy)

        try {
            val thrown = assertFailsWith<Error> {
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
                    memberSeen.complete(current.getMemberName().orEmpty())
                    senderSeen.complete(current.getSender().orEmpty())
                }
            }
        }
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, managerPath)
        val seenMembers = mutableListOf<String>()
        val added = CompletableDeferred<Unit>()
        val removed = CompletableDeferred<Unit>()
        val addedRegistration = proxy.registerSignalHandler(
            InterfaceName("org.freedesktop.DBus.ObjectManager"),
            SignalName("InterfacesAdded")
        ) {
            seenMembers += it.getMemberName().orEmpty()
            added.complete(Unit)
        }
        val removedRegistration = proxy.registerSignalHandler(
            InterfaceName("org.freedesktop.DBus.ObjectManager"),
            SignalName("InterfacesRemoved")
        ) {
            seenMembers += it.getMemberName().orEmpty()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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
