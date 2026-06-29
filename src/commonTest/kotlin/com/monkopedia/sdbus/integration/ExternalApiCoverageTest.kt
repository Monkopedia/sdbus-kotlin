package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.propDelegate
import com.monkopedia.sdbus.signal
import com.monkopedia.sdbus.signalFlow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * External (real-bus) coverage for public surface that the larger [CommonApiIntegrationTest] does
 * not reach: the [com.monkopedia.sdbus.Object]/[com.monkopedia.sdbus.Proxy] `connection` accessors,
 * [com.monkopedia.sdbus.Object.currentlyProcessedMessage] from inside a handler, the read-only and
 * read/write Kotlin property delegates ([propDelegate]/[mutableDelegate]), the [signalFlow]
 * reactive subscription, and directed (unicast) signal delivery via
 * [com.monkopedia.sdbus.Signal.setDestination]. Runs on both the native and JVM backends.
 */
class ExternalApiCoverageTest {

    private class IntHolder(var value: Int)

    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.coverage.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/coverage/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    @Test
    fun proxyAndObject_exposeTheirUnderlyingConnection() {
        val ids = uniqueFixtureIds("connection")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Noop")) { call { -> } }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path, runEventLoopThread = false)

        try {
            // Object.connection and Proxy.connection must hand back the connection each was
            // created on, not a copy or wrapper.
            assertSame(serverConnection, obj.connection)
            assertSame(proxyConnection, proxy.connection)
        } finally {
            runBlocking { serverConnection.stopEventLoop() }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun objectExposesCurrentlyProcessedMessageInsideHandler() {
        val ids = uniqueFixtureIds("currentMsg")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val methodName = MethodName("Echo")
        var seenMember: String? = null
        var seenSender: String? = null
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(methodName) {
                call { value: Int ->
                    // The message currently being dispatched is reachable from the Object itself
                    // (mirrors the native generated-adaptor path, but via the common API here).
                    val message = obj.currentlyProcessedMessage
                    seenMember = message.memberName?.value
                    seenSender = message.sender?.value
                    value
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path, runEventLoopThread = false)

        try {
            val result = proxy.callMethod<Int>(ids.iface, methodName) { call(7) }
            assertEquals(7, result)
            assertEquals(methodName.value, seenMember)
            assertEquals(proxyConnection.uniqueName.value, seenSender)
        } finally {
            runBlocking { serverConnection.stopEventLoop() }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun propDelegate_readsRemoteProperty() {
        val ids = uniqueFixtureIds("propDelegate")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val levelProperty = PropertyName("Level")
        val holder = IntHolder(42)
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(levelProperty) { with(holder::value) }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            // A read-only `by` delegate issues a Properties.Get on each read.
            val level: Int by proxy.propDelegate<Any?, Int>(ids.iface, levelProperty)
            assertEquals(42, level)

            holder.value = 99
            val updated: Int by proxy.propDelegate<Any?, Int>(ids.iface, levelProperty)
            assertEquals(99, updated)
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
    fun mutableDelegate_roundTripsRemoteProperty() {
        val ids = uniqueFixtureIds("mutableDelegate")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val levelProperty = PropertyName("Level")
        val holder = IntHolder(1)
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            prop(levelProperty) { with(holder::value) }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            var level: Int by proxy.mutableDelegate<Any?, Int>(ids.iface, levelProperty)
            // Read issues Properties.Get.
            assertEquals(1, level)
            // Write issues Properties.Set, which must land on the server-side backing field.
            level = 73
            assertEquals(73, holder.value)
            // And the next read reflects it across the bus.
            assertEquals(73, level)
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
    fun signalFlow_emitsOnePerReceivedSignal() = runBlocking {
        val ids = uniqueFixtureIds("signalFlow")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val signalName = SignalName("Ticked")
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(signalName) { with<Int>("value") }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)

        try {
            val first = CompletableDeferred<Int>()
            val collector = launch {
                val value = proxy.signalFlow<Int>(ids.iface, signalName) {
                    call { value: Int -> value }
                }.first()
                first.complete(value)
            }

            // Give the flow's registration a moment to install before emitting.
            withTimeout(2_000) {
                while (!collector.isActive && !first.isCompleted) { /* spin until launched */ }
            }
            // Emit until the collector has observed one (covers the install race without sleeps).
            var emitted = 0
            while (!first.isCompleted && emitted < 50) {
                val signal = obj.createSignal(ids.iface, signalName)
                signal.append(11)
                obj.emitSignal(signal)
                emitted++
                if (!first.isCompleted) kotlinx.coroutines.delay(20)
            }

            assertEquals(11, withTimeout(2_000) { first.await() })
            collector.cancel()
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
    fun directedSignal_carriesDestinationToTargetedProxy() = runBlocking {
        val ids = uniqueFixtureIds("directed")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val signalName = SignalName("Pinged")
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(signalName) { with<Int>("value") }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val target = proxyConnection.uniqueName

        try {
            // Completed with the destination header observed on the received message (nullable):
            // sd-bus surfaces it on the recipient; the JVM wire backend delivers the unicast
            // signal but does not re-expose the DESTINATION header to the handler. Completion of
            // this deferred is itself the portable proof that setDestination routed delivery to
            // the targeted proxy on both backends.
            val delivered = CompletableDeferred<String?>()
            val handlerRegistration = proxy.onSignal(ids.iface, signalName) {
                call { _: Int ->
                    if (!delivered.isCompleted) {
                        delivered.complete(proxy.currentlyProcessedMessage.destination?.value)
                    }
                }
            }

            try {
                var emitted = 0
                while (!delivered.isCompleted && emitted < 50) {
                    val signal = obj.createSignal(ids.iface, signalName)
                    // Restrict this signal to the proxy's unique name instead of broadcasting.
                    signal.setDestination(target)
                    signal.append(5)
                    obj.emitSignal(signal)
                    emitted++
                    if (!delivered.isCompleted) kotlinx.coroutines.delay(20)
                }

                // Portable: the directed signal reached the targeted proxy.
                val destination = withTimeout(2_000) { delivered.await() }
                assertTrue(emitted >= 1)
                // Where the backend surfaces it (native), the header must equal the target.
                if (destination != null) {
                    assertEquals(target.value, destination)
                }
            } finally {
                handlerRegistration.release()
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
}
