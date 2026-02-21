package com.monkopedia.sdbus

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmSignalPropertyUnsupportedTest {
    @Test
    fun objectEmitSignal_isSupported() {
        val connection = createBusConnection()
        val obj = createObject(connection, ObjectPath("/org/example/unsupported/signal"))
        val signal = obj.createSignal(
            InterfaceName("org.example.Unsupported"),
            SignalName("Changed")
        )

        try {
            obj.emitSignal(signal)
        } finally {
            obj.release()
            connection.release()
        }
    }

    @Test
    fun objectEmitPropertiesChanged_isSupported() {
        val service = ServiceName("org.example.Unsupported.Properties")
        val path = ObjectPath("/org/example/unsupported/properties")
        val iface = InterfaceName("org.example.Unsupported")
        val serverConnection = createBusConnection(service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, path)
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val proxy = createProxy(proxyConnection, service, path)
        val latch = CountDownLatch(1)
        val seenMember = arrayOf<String?>(null)

        try {
            val signalResource = proxy.registerSignalHandler(
                InterfaceName("org.freedesktop.DBus.Properties"),
                SignalName("PropertiesChanged")
            ) { message ->
                seenMember[0] = message.getMemberName()
                latch.countDown()
            }
            obj.emitPropertiesChangedSignal(iface)
            obj.emitPropertiesChangedSignal(iface, listOf(PropertyName("state")))
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals("PropertiesChanged", seenMember[0])
            signalResource.release()
        } finally {
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
