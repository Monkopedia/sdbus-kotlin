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
    fun noArgInterfacesAddedEnumeratesStandardInterfaces() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.iastd$id")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/iastd$id")
        val childPath = ObjectPath("/com/monkopedia/sdbus/iastd$id/child")
        val iface = InterfaceName("com.monkopedia.sdbus.iastd$id.Iface")
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

        val seen = CompletableDeferred<Map<InterfaceName, Map<PropertyName, Variant>>>()
        val sigReg = proxy.onSignal(
            ObjectManagerProxy.INTERFACE_NAME,
            SignalName("InterfacesAdded")
        ) {
            call { _: ObjectPath, ifaces: Map<InterfaceName, Map<PropertyName, Variant>> ->
                if (!seen.isCompleted) seen.complete(ifaces)
            }
        }

        try {
            // The no-argument form maps onto sd_bus_emit_object_added, which advertises the
            // object's standard interfaces alongside its vtables. The object is not itself an
            // ObjectManager, so that one must NOT appear.
            obj.emitInterfacesAddedSignal()
            val ifaces = withTimeout(2_000) { seen.await() }
            assertEquals(
                setOf(
                    InterfaceName("org.freedesktop.DBus.Peer"),
                    InterfaceName("org.freedesktop.DBus.Introspectable"),
                    InterfaceName("org.freedesktop.DBus.Properties"),
                    iface
                ),
                ifaces.keys
            )
            // The standard interfaces carry no properties; the vtable interface still carries its
            // current values (#143).
            assertEquals(emptyMap(), ifaces[InterfaceName("org.freedesktop.DBus.Peer")])
            assertEquals(42, ifaces[iface]?.get(levelProp)?.get<Int>())
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
    fun noArgInterfacesAddedEnumeratesObjectManagerWhenPresent() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.iaom$id")
        val path = ObjectPath("/com/monkopedia/sdbus/iaom$id")
        val iface = InterfaceName("com.monkopedia.sdbus.iaom$id.Iface")

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
        }
        val manager = obj.addObjectManager()
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, path)

        val seen = CompletableDeferred<Set<InterfaceName>>()
        val sigReg = proxy.onSignal(
            ObjectManagerProxy.INTERFACE_NAME,
            SignalName("InterfacesAdded")
        ) {
            call { _: ObjectPath, ifaces: Map<InterfaceName, Map<PropertyName, Variant>> ->
                if (!seen.isCompleted) seen.complete(ifaces.keys)
            }
        }

        try {
            obj.emitInterfacesAddedSignal()
            assertEquals(
                setOf(
                    InterfaceName("org.freedesktop.DBus.Peer"),
                    InterfaceName("org.freedesktop.DBus.Introspectable"),
                    InterfaceName("org.freedesktop.DBus.Properties"),
                    ObjectManagerProxy.INTERFACE_NAME,
                    iface
                ),
                withTimeout(2_000) { seen.await() }
            )
        } finally {
            sigReg.release()
            manager.release()
            reg.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }

    @Test
    fun noArgInterfacesRemovedEnumeratesStandardInterfaces() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.irstd$id")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/irstd$id")
        val childPath = ObjectPath("/com/monkopedia/sdbus/irstd$id/child")
        val iface = InterfaceName("com.monkopedia.sdbus.irstd$id.Iface")

        val server = createBusConnection(service)
        val client = createBusConnection()
        val managerObj = createObject(server, managerPath)
        val manager = managerObj.addObjectManager()
        val obj = createObject(server, childPath)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, managerPath)

        val seen = CompletableDeferred<List<InterfaceName>>()
        val sigReg = proxy.onSignal(
            ObjectManagerProxy.INTERFACE_NAME,
            SignalName("InterfacesRemoved")
        ) {
            call { _: ObjectPath, ifaces: List<InterfaceName> ->
                if (!seen.isCompleted) seen.complete(ifaces)
            }
        }

        try {
            // sd_bus_emit_object_removed withdraws exactly what sd_bus_emit_object_added
            // advertised, so the removal must name the standard interfaces too.
            obj.emitInterfacesRemovedSignal()
            assertEquals(
                setOf(
                    InterfaceName("org.freedesktop.DBus.Peer"),
                    InterfaceName("org.freedesktop.DBus.Introspectable"),
                    InterfaceName("org.freedesktop.DBus.Properties"),
                    iface
                ),
                withTimeout(2_000) { seen.await() }.toSet()
            )
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
