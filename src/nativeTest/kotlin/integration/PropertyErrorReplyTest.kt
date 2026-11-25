package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.getProperty
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.setProperty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

/**
 * A throwing property getter/setter must reply with the same error name and message as a throwing
 * method (#283): a plain exception becomes `org.freedesktop.DBus.Error.Failed` carrying its message,
 * and an [SdbusException] keeps its own name with [SdbusException.errorMessage] as the message.
 *
 * Native-only: on JVM a same-process server/proxy pair takes the local-dispatch path, which reports
 * property throws differently (see #283), so the commonTest same-process fixture can't hold this.
 */
class PropertyErrorReplyTest {

    private val iface = InterfaceName("com.monkopedia.sdbus.propertyError.Interface")
    private val throwingMethod = MethodName("Throw")
    private val throwingGetter = PropertyName("throwingGetter")
    private val throwingSetter = PropertyName("throwingSetter")

    // Keeps the throwing callbacks' inferred types non-Nothing (Nothing has no serializer).
    private val alwaysThrows = true

    @Test
    fun plainException_repliesFailedWithItsMessage() = assertAllReply(
        { RuntimeException("boom") },
        expectedName = "org.freedesktop.DBus.Error.Failed",
        expectedMessage = "boom"
    )

    @Test
    fun sdbusException_repliesItsNameAndErrorMessage() = assertAllReply(
        { SdbusException("com.example.Error.Busy", "busy-detail") },
        expectedName = "com.example.Error.Busy",
        expectedMessage = "busy-detail"
    )

    private fun assertAllReply(
        error: () -> Throwable,
        expectedName: String,
        expectedMessage: String
    ): Unit = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.propertyError.s$id")
        val path = ObjectPath("/com/monkopedia/sdbus/propertyError/s$id")
        val serverConnection = createBusConnection(service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, path)
        val registration = obj.addVTable(iface) {
            // Positive control: the method path already maps errors through toError() (#142).
            method(throwingMethod) {
                call { _: Int -> if (alwaysThrows) throw error() else 0 }
            }
            prop(throwingGetter) {
                withGetter { if (alwaysThrows) throw error() else 0 }
            }
            prop(throwingSetter) {
                withGetter { 0 }
                withSetter<Int> { throw error() }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, service, path)

        try {
            val calls: List<Pair<String, Proxy.() -> Unit>> = listOf(
                "method" to { callMethod<Int>(iface, throwingMethod) { call(1) } },
                "getter" to { getProperty<Int>(iface, throwingGetter) },
                "setter" to { setProperty(iface, throwingSetter, 1) }
            )
            // Compared as one list so a failure shows every call's reply, not just the first.
            val replies = calls.map { (label, invoke) ->
                val thrown = assertFailsWith<SdbusException>(label) { proxy.invoke() }
                "$label: ${thrown.name} / ${thrown.errorMessage}"
            }
            assertEquals(calls.map { "${it.first}: $expectedName / $expectedMessage" }, replies)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
