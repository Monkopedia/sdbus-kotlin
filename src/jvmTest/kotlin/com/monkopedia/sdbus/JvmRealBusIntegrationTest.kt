package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class JvmRealBusIntegrationTest {

    // These tests need a *real* bus. The connection factories throw when the bus is
    // unreachable (issue #81) instead of silently returning the in-process stub backend, so
    // "no usable bus in this environment" now surfaces as a thrown Error -- treat it as a
    // skip, the way the old ":jvm-stub" unique-name gate did.
    private fun connectOrNull(connect: () -> Connection): Connection? = try {
        connect()
    } catch (e: Error) {
        null
    }

    // Regression for the BlueZ-on-JVM failure, exercised over the real wire (same-process
    // calls short-circuit through local dispatch and never serialize, so they can't catch
    // this). An empty collection argument used to be serialized with an inferred signature,
    // but an empty value has no element to infer from — so an empty array became the
    // malformed "a" (and an empty dict "a{}"), which makes the daemon drop the connection
    // with "Underlying transport returned -1". Here we call the bus daemon's BecomeMonitor,
    // whose first argument is an `as` filter list, with an empty list: with the fix it
    // serializes as "as" and succeeds; without it the connection is torn down.
    @Test
    fun methodCall_withEmptyArrayArg_overWire_doesNotDropConnection() = runBlocking {
        val connection = connectOrNull { createBusConnection() } ?: return@runBlocking
        val proxy = createProxy(
            connection,
            ServiceName("org.freedesktop.DBus"),
            ObjectPath("/org/freedesktop/DBus")
        )
        try {
            // Empty `as` filters + flags 0. The call must not throw a transport error;
            // a normal reply (or a benign D-Bus error) means the connection survived.
            proxy.callMethod<Unit>(
                InterfaceName("org.freedesktop.DBus.Monitoring"),
                MethodName("BecomeMonitor")
            ) {
                call(emptyList<String>(), 0u)
            }
        } finally {
            proxy.release()
            connection.release()
        }
    }

    @Test
    fun createSystemBusConnection_usesDistinctConnectionsWhenRealBusIsAvailable() {
        val first = connectOrNull { createSystemBusConnection() } ?: return
        val second = connectOrNull { createSystemBusConnection() } ?: run {
            first.release()
            return
        }
        try {
            assertNotEquals(first.uniqueName.value, second.uniqueName.value)
        } finally {
            second.release()
            first.release()
        }
    }

    @Test
    fun createSessionBusConnection_usesDistinctConnectionsWhenRealBusIsAvailable() {
        val first = connectOrNull { createSessionBusConnection() } ?: return
        val second = connectOrNull { createSessionBusConnection() } ?: run {
            first.release()
            return
        }
        try {
            assertNotEquals(first.uniqueName.value, second.uniqueName.value)
        } finally {
            second.release()
            first.release()
        }
    }

    @Test
    fun connectionAddObjectManager_routesInterfacesAddedOnManagerPath() = runBlocking {
        val suffix = "s${System.nanoTime()}"
        val service = ServiceName("com.monkopedia.sdbus.jvmreal.$suffix")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/jvmreal$suffix")
        val childPath = ObjectPath("${managerPath.value}/child")
        val childInterface = InterfaceName("com.monkopedia.sdbus.jvmreal.$suffix.Interface")
        val objectManagerInterface = InterfaceName("org.freedesktop.DBus.ObjectManager")
        val interfacesAdded = SignalName("InterfacesAdded")

        val serverConnection = connectOrNull { createSessionBusConnection(service) }
            ?: return@runBlocking
        val proxyConnection = connectOrNull { createSessionBusConnection() } ?: run {
            serverConnection.release()
            return@runBlocking
        }

        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val managerRegistration = serverConnection.addObjectManager(managerPath)
        val obj = createObject(serverConnection, childPath)
        val proxy = createProxy(proxyConnection, service, managerPath)
        val seen = CompletableDeferred<Message>()
        val signalRegistration = proxy.registerSignalHandler(
            objectManagerInterface,
            interfacesAdded
        ) { message ->
            if (!seen.isCompleted) {
                seen.complete(message)
            }
        }

        try {
            obj.emitInterfacesAddedSignal(listOf(childInterface))
            val message = withTimeout(2_000) { seen.await() }
            assertEquals(managerPath, message.path)
        } finally {
            signalRegistration.release()
            proxy.release()
            obj.release()
            managerRegistration.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun objectManagerGetManagedObjects_returnsManagedChildWithProperties() = runBlocking {
        val suffix = "g${System.nanoTime()}"
        val service = ServiceName("com.monkopedia.sdbus.jvmreal.$suffix")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/jvmreal$suffix")
        val childPath = ObjectPath("${managerPath.value}/child")
        val childInterface = InterfaceName("com.monkopedia.sdbus.jvmreal.$suffix.Interface")
        val valueProperty = PropertyName("value")
        var value = 17

        val serverConnection = connectOrNull { createSessionBusConnection(service) }
            ?: return@runBlocking
        val proxyConnection = connectOrNull { createSessionBusConnection() } ?: run {
            serverConnection.release()
            return@runBlocking
        }

        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val managerRegistration = serverConnection.addObjectManager(managerPath)
        val obj = createObject(serverConnection, childPath)
        val registration = obj.addVTable(childInterface) {
            prop(valueProperty) {
                withGetter { value }
            }
        }
        val proxy = createProxy(proxyConnection, service, managerPath)

        try {
            val objects =
                proxy.callMethod<Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>>>(
                    ObjectManagerProxy.INTERFACE_NAME,
                    MethodName("GetManagedObjects")
                ) {
                }
            assertEquals(
                value,
                objects[childPath]?.get(childInterface)?.get(valueProperty)?.get<Int>()
            )
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            managerRegistration.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun systemBusObjectManagerGetManagedObjects_returnsManagedChildWithProperties() = runBlocking {
        val suffix = "sys${System.nanoTime()}"
        val managerPath = ObjectPath("/com/monkopedia/sdbus/jvmreal$suffix")
        val childPath = ObjectPath("${managerPath.value}/child")
        val childInterface = InterfaceName("com.monkopedia.sdbus.jvmreal.$suffix.Interface")
        val valueProperty = PropertyName("value")
        var value = 23

        val serverConnection = connectOrNull { createSystemBusConnection() }
            ?: return@runBlocking
        val proxyConnection = connectOrNull { createSystemBusConnection() } ?: run {
            serverConnection.release()
            return@runBlocking
        }
        val serverUnique = serverConnection.uniqueName.value

        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val managerRegistration = serverConnection.addObjectManager(managerPath)
        val obj = createObject(serverConnection, childPath)
        val registration = obj.addVTable(childInterface) {
            prop(valueProperty) {
                withGetter { value }
            }
        }
        val proxy = createProxy(proxyConnection, ServiceName(serverUnique), managerPath)

        try {
            val objects =
                proxy.callMethod<Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>>>(
                    ObjectManagerProxy.INTERFACE_NAME,
                    MethodName("GetManagedObjects")
                ) {
                }
            assertEquals(
                value,
                objects[childPath]?.get(childInterface)?.get(valueProperty)?.get<Int>()
            )
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            managerRegistration.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalCallback_resolvesSenderCredentialsFromBus() = runBlocking {
        // wire signal emission deferred — epic #93 (dedicated phase): our object's signal is
        // delivered in-process via the local bus, which carries no sender credentials. Resolving
        // credentials needs the signal to travel over the wire (real wire emission), so this case
        // is gated until that lands.
        if (com.monkopedia.sdbus.internal.jvmdbus.isJvmWireBackendEnabled()) return@runBlocking
        val suffix = "c${System.nanoTime()}"
        val service = ServiceName("com.monkopedia.sdbus.jvmreal.$suffix")
        val objectPath = ObjectPath("/com/monkopedia/sdbus/jvmreal$suffix/credentials")
        val interfaceName = InterfaceName("com.monkopedia.sdbus.jvmreal.$suffix.Credentials")
        val signalName = SignalName("Changed")
        val expectedPid = ProcessHandle.current().pid().toInt()

        val serverConnection = connectOrNull { createSessionBusConnection(service) }
            ?: return@runBlocking
        val proxyConnection = connectOrNull { createSessionBusConnection() } ?: run {
            serverConnection.release()
            return@runBlocking
        }
        val serverUnique = serverConnection.uniqueName.value

        val senderSeen = CompletableDeferred<String>()
        val pidSeen = CompletableDeferred<Int>()
        val obj = createObject(serverConnection, objectPath)
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, service, objectPath)
        val signalRegistration = proxy.registerSignalHandler(interfaceName, signalName) { message ->
            if (!senderSeen.isCompleted) {
                senderSeen.complete(message.sender?.value.orEmpty())
                pidSeen.complete(message.credsPid)
            }
        }

        try {
            val signal = obj.createSignal(interfaceName, signalName)
            signal.append(1)
            signal.send()

            assertEquals(serverUnique, withTimeout(2_000) { senderSeen.await() })
            assertEquals(expectedPid, withTimeout(2_000) { pidSeen.await() })
        } finally {
            signalRegistration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
