package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Parity regression (#141): the ObjectManager `InterfacesAdded` payload and the no-argument
 * `PropertiesChanged` signal must carry the object's CURRENT property values on both backends.
 * Native (sd-bus) fills them via sd_bus_emit_*; the JVM wire backend used to emit empty property
 * maps, so an ObjectManager consumer that reads initial device state from these signals (the
 * standard BlueZ pattern) got nothing on JVM. Runs on both targets.
 */
class SignalPayloadParityTest {

    private class Holder(var value: Int)

    @Test
    fun interfacesAddedCarriesCurrentPropertyValues() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.iap$id")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/iap$id")
        val childPath = ObjectPath("/com/monkopedia/sdbus/iap$id/child")
        val iface = InterfaceName("com.monkopedia.sdbus.iap$id.Iface")
        val levelProp = PropertyName("Level")
        val holder = Holder(42)

        val server = createBusConnection(service)
        val client = createBusConnection()
        val managerObj = createObject(server, managerPath)
        val manager = managerObj.addObjectManager()
        val obj = createObject(server, childPath)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
            prop(levelProp) { with(holder::value) }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, managerPath)

        val seenLevel = CompletableDeferred<Int?>()
        val sigReg = proxy.onSignal(
            ObjectManagerProxy.INTERFACE_NAME,
            SignalName("InterfacesAdded")
        ) {
            call { _: ObjectPath, ifaces: Map<InterfaceName, Map<PropertyName, Variant>> ->
                if (!seenLevel.isCompleted) {
                    seenLevel.complete(ifaces[iface]?.get(levelProp)?.get<Int>())
                }
            }
        }

        try {
            obj.emitInterfacesAddedSignal(listOf(iface))
            // The InterfacesAdded payload must report the property's current value, not an empty map.
            assertEquals(42, withTimeout(2_000) { seenLevel.await() })
        } finally {
            sigReg.release()
            reg.release()
            manager.release()
            managerObj.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }

    @Test
    fun noArgPropertiesChangedCarriesAllInterfaceProperties() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.pcall$id")
        val path = ObjectPath("/com/monkopedia/sdbus/pcall$id")
        val iface = InterfaceName("com.monkopedia.sdbus.pcall$id.Iface")
        val levelProp = PropertyName("Level")
        val holder = Holder(42)

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
            prop(levelProp) { with(holder::value) }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, path)

        val seenLevel = CompletableDeferred<Int?>()
        val sigReg = proxy.onSignal(
            PropertiesProxy.INTERFACE_NAME,
            SignalName("PropertiesChanged")
        ) {
            call { _: InterfaceName, changed: Map<PropertyName, Variant>, _: List<PropertyName> ->
                if (!seenLevel.isCompleted) {
                    seenLevel.complete(changed[levelProp]?.get<Int>())
                }
            }
        }

        try {
            // No-argument form: native emits every property of the interface; JVM used to emit none.
            obj.emitPropertiesChangedSignal(iface)
            assertEquals(42, withTimeout(2_000) { seenLevel.await() })
        } finally {
            sigReg.release()
            reg.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }
}
