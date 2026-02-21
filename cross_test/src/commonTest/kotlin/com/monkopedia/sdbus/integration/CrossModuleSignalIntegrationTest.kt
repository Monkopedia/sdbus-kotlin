package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class CrossModuleSignalIntegrationTest {
    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.cross.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/cross/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    @Test
    fun signalRoundTrip_supportsMapPayload() = runBlocking {
        val ids = uniqueFixtureIds("signalMap")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("MapChanged")) {
                with<Map<Int, String>>("value")
            }
        }
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<Map<Int, String>>()
        val handler = proxy.onSignal(ids.iface, SignalName("MapChanged")) {
            call { value: Map<Int, String> ->
                seen.complete(value)
            }
        }

        try {
            val expected = mapOf(0 to "zero", 1 to "one")
            obj.emitSignal(ids.iface, SignalName("MapChanged")) {
                call(expected)
            }
            assertEquals(expected, withTimeout(2_000) { seen.await() })
        } finally {
            handler.release()
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
    fun signalRoundTrip_supportsLargeMapPayload() = runBlocking {
        val ids = uniqueFixtureIds("signalLargeMap")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("MapChanged")) {
                with<Map<Int, String>>("value")
            }
        }
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<Map<Int, String>>()
        val handler = proxy.onSignal(ids.iface, SignalName("MapChanged")) {
            call { value: Map<Int, String> ->
                seen.complete(value)
            }
        }

        try {
            val expected = buildMap {
                for (i in 0 until 5_000) {
                    put(i, "value-$i")
                }
            }
            obj.emitSignal(ids.iface, SignalName("MapChanged")) {
                call(expected)
            }
            val actual = withTimeout(5_000) { seen.await() }
            assertEquals("value-0", actual[0])
            assertEquals("value-4999", actual[4_999])
        } finally {
            handler.release()
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
    fun signalRoundTrip_supportsVariantPayload() = runBlocking {
        val ids = uniqueFixtureIds("signalVariant")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("VariantChanged")) {
                with<Variant>("value")
            }
        }
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val seen = CompletableDeferred<Variant>()
        val handler = proxy.onSignal(ids.iface, SignalName("VariantChanged")) {
            call { value: Variant ->
                seen.complete(value)
            }
        }

        try {
            obj.emitSignal(ids.iface, SignalName("VariantChanged")) {
                call(Variant(3.14))
            }
            val value = withTimeout(2_000) { seen.await() }
            assertEquals(3.14, value.get<Double>(), 0.01)
        } finally {
            handler.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
