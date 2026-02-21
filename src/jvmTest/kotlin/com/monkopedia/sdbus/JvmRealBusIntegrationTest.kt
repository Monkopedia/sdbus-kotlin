package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class JvmRealBusIntegrationTest {
    @Test
    fun createSystemBusConnection_usesDistinctConnectionsWhenRealBusIsAvailable() {
        val first = createSystemBusConnection()
        val second = createSystemBusConnection()
        try {
            val firstUnique = first.getUniqueName().value
            val secondUnique = second.getUniqueName().value
            if (firstUnique == ":jvm-stub" || secondUnique == ":jvm-stub") return
            assertNotEquals(firstUnique, secondUnique)
        } finally {
            second.release()
            first.release()
        }
    }

    @Test
    fun createSessionBusConnection_usesDistinctConnectionsWhenRealBusIsAvailable() {
        val first = createSessionBusConnection()
        val second = createSessionBusConnection()
        try {
            val firstUnique = first.getUniqueName().value
            val secondUnique = second.getUniqueName().value
            if (firstUnique == ":jvm-stub" || secondUnique == ":jvm-stub") return
            assertNotEquals(firstUnique, secondUnique)
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

        val serverConnection = createSessionBusConnection(service)
        val proxyConnection = createSessionBusConnection()
        val serverUnique = serverConnection.getUniqueName().value
        val proxyUnique = proxyConnection.getUniqueName().value
        if (serverUnique == ":jvm-stub" || proxyUnique == ":jvm-stub") {
            proxyConnection.release()
            serverConnection.release()
            return@runBlocking
        }

        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            assertEquals(managerPath.value, message.getPath())
        } finally {
            signalRegistration.release()
            proxy.release()
            obj.release()
            managerRegistration.release()
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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

        val serverConnection = createSessionBusConnection(service)
        val proxyConnection = createSessionBusConnection()
        val serverUnique = serverConnection.getUniqueName().value
        val proxyUnique = proxyConnection.getUniqueName().value
        if (serverUnique == ":jvm-stub" || proxyUnique == ":jvm-stub") {
            proxyConnection.release()
            serverConnection.release()
            return@runBlocking
        }

        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
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

        val serverConnection = createSystemBusConnection()
        val proxyConnection = createSystemBusConnection()
        val serverUnique = serverConnection.getUniqueName().value
        val proxyUnique = proxyConnection.getUniqueName().value
        if (serverUnique == ":jvm-stub" || proxyUnique == ":jvm-stub") {
            proxyConnection.release()
            serverConnection.release()
            return@runBlocking
        }

        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalCallback_resolvesSenderCredentialsFromBus() = runBlocking {
        val suffix = "c${System.nanoTime()}"
        val service = ServiceName("com.monkopedia.sdbus.jvmreal.$suffix")
        val objectPath = ObjectPath("/com/monkopedia/sdbus/jvmreal$suffix/credentials")
        val interfaceName = InterfaceName("com.monkopedia.sdbus.jvmreal.$suffix.Credentials")
        val signalName = SignalName("Changed")
        val expectedPid = ProcessHandle.current().pid().toInt()

        val serverConnection = createSessionBusConnection(service)
        val proxyConnection = createSessionBusConnection()
        val serverUnique = serverConnection.getUniqueName().value
        val proxyUnique = proxyConnection.getUniqueName().value
        if (serverUnique == ":jvm-stub" || proxyUnique == ":jvm-stub") {
            proxyConnection.release()
            serverConnection.release()
            return@runBlocking
        }

        val senderSeen = CompletableDeferred<String>()
        val pidSeen = CompletableDeferred<Int>()
        val obj = createObject(serverConnection, objectPath)
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, service, objectPath)
        val signalRegistration = proxy.registerSignalHandler(interfaceName, signalName) { message ->
            if (!senderSeen.isCompleted) {
                senderSeen.complete(message.getSender().orEmpty())
                pidSeen.complete(message.getCredsPid())
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
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
