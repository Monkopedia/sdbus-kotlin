package com.monkopedia.sdbus.integration

import com.example.myservice1.InterestingInterfaceAdaptor
import com.example.myservice1.InterestingInterfaceProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class GeneratedCodeIntegrationTest {
    @Test
    fun generatedProxyAndAdaptor_roundTripWithRealBus() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.generated.case$id")
        val path = ObjectPath("/com/monkopedia/sdbus/generated/case$id")
        val serverConnection = createBusConnection(service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, path)
        val adaptor = object : InterestingInterfaceAdaptor(obj) {
            private var nextId = 1u

            override suspend fun addContact(name: String, email: String): UInt {
                val assigned = nextId
                nextId += 1u
                return assigned
            }
        }
        adaptor.register()
        serverConnection.enterEventLoopAsync()
        proxyConnection.enterEventLoopAsync()
        val rawProxy = createProxy(proxyConnection, service, path)
        val proxy = InterestingInterfaceProxy(rawProxy)

        try {
            assertEquals(1u, proxy.addContact("alpha", "alpha@example.com"))
            assertEquals(2u, proxy.addContact("beta", "beta@example.com"))
        } finally {
            rawProxy.release()
            obj.release()
            proxyConnection.leaveEventLoop()
            serverConnection.leaveEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
