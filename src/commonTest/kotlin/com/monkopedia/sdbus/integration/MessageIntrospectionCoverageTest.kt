package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.containsValueOfType
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * External (real-bus) coverage for [Variant] type-introspection on a value received over the bus:
 * [Variant.containsValueOfType] and [Variant.peekValueType] (which other integration suites only
 * exercise indirectly via extraction). Runs on both backends.
 */
class MessageIntrospectionCoverageTest {

    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.msgintro.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/msgintro/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    @Test
    fun variantReceivedOverBus_supportsTypeIntrospection() {
        val ids = uniqueFixtureIds("variant")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val methodName = MethodName("Wrap")
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(methodName) {
                call { value: Int -> Variant(value) }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path, runEventLoopThread = false)

        try {
            val variant = proxy.callMethod<Variant>(ids.iface, methodName) { call(42) }

            // Introspect the contained type before extracting it.
            assertTrue(variant.containsValueOfType<Int>())
            assertFalse(variant.containsValueOfType<String>())
            assertEquals("i", variant.peekValueType())
            assertEquals(42, variant.get<Int>())
        } finally {
            runBlocking { serverConnection.stopEventLoop() }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
