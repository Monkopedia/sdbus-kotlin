package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.JvmConnection
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StubJvmDbusBackendTest {
    @Test
    fun signalSend_roundTripsThroughStubProxyHandler() {
        val backend = StubJvmDbusBackend()
        val service = ServiceName("org.example.stub.signal")
        val path = ObjectPath("/org/example/stub/signal")
        val iface = InterfaceName("org.example.stub.Interface")
        val signalName = SignalName("Changed")
        val connection = JvmConnection(
            backend.createConnection(JvmBusType.DEFAULT, null, service, null)
        )
        val proxy = backend.createProxy(connection, service, path, dontRunEventLoopThread = true)
        val obj = backend.createObject(connection, path)
        val seen = CountDownLatch(1)
        var payload: String? = null
        val registration = proxy.registerSignalHandler(iface, signalName) { signal ->
            signal.rewind(false)
            payload = signal.readString()
            seen.countDown()
        }

        try {
            val signal = obj.createSignal(iface, signalName)
            signal.append("hello")
            signal.send()

            assertTrue(seen.await(2, TimeUnit.SECONDS))
            assertEquals("hello", payload)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            connection.release()
        }
    }

    @Test
    fun callMethodAsync_invokesCallbackThroughStubProxy() {
        val backend = StubJvmDbusBackend()
        val service = ServiceName("org.example.stub.method")
        val path = ObjectPath("/org/example/stub/method")
        val iface = InterfaceName("org.example.stub.Interface")
        val method = MethodName("AddOne")
        val connection = JvmConnection(
            backend.createConnection(JvmBusType.DEFAULT, null, service, null)
        )
        val proxy = backend.createProxy(connection, service, path, dontRunEventLoopThread = true)
        val seen = CountDownLatch(1)
        var value: Int? = null
        var callbackError: com.monkopedia.sdbus.Error? = null

        JvmStaticDispatch.register(path.value, iface.value, method.value, argCount = 1) { args ->
            (args[0] as Int) + 1
        }

        try {
            val call = proxy.createMethodCall(iface, method)
            call.append(41)
            val pending = proxy.callMethodAsync(call) { reply, error ->
                callbackError = error
                reply.rewind(false)
                value = reply.readInt()
                seen.countDown()
            }

            assertTrue(seen.await(2, TimeUnit.SECONDS))
            assertNull(callbackError)
            assertEquals(42, value)
            assertFalse(pending.isPending())
        } finally {
            JvmStaticDispatch.unregister(path.value, iface.value, method.value, argCount = 1)
            proxy.release()
            connection.release()
        }
    }
}
