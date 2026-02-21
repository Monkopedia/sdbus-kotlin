package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmStaticDispatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class JvmScaffoldTest {
    @Test
    fun createBusConnection_hasStubUniqueName() {
        val connection = createBusConnection()
        assertTrue(connection.getUniqueName().value.isNotBlank())
    }

    @Test
    fun proxySyncCall_isExplicitlyUnsupported() {
        val proxy = createProxy(ServiceName("org.example"), ObjectPath("/org/example"))
        val call = proxy.createMethodCall(InterfaceName("org.example"), MethodName("Ping"))

        assertFailsWith<Error> {
            proxy.callMethod(call)
        }
    }

    @Test
    fun proxyCall_usesStaticDispatchRegistry() {
        JvmStaticDispatch.register(
            objectPath = "/org/example",
            interfaceName = "org.example.Static",
            methodName = "Add",
            argCount = 2
        ) { args ->
            (args[0] as Int) + (args[1] as Int)
        }
        try {
            val proxy = createProxy(ServiceName("org.example"), ObjectPath("/org/example"))
            val call = proxy.createMethodCall(
                InterfaceName("org.example.Static"),
                MethodName("Add")
            )
            call.append(2)
            call.append(3)

            val reply = proxy.callMethod(call)
            reply.rewind(false)
            assertEquals(5, reply.readInt())
        } finally {
            JvmStaticDispatch.unregister(
                objectPath = "/org/example",
                interfaceName = "org.example.Static",
                methodName = "Add",
                argCount = 2
            )
        }
    }

    @Test
    fun addVTable_autoRegistersStaticDispatchAndUnregistersOnRelease() {
        val connection = createBusConnection()
        val path = ObjectPath("/org/example/auto")
        val obj = createObject(connection, path)
        val registration = obj.addVTable(InterfaceName("org.example.Auto")) {
            method(MethodName("Add")) {
                call { a: Int, b: Int -> a + b }
            }
        }
        val proxy = createProxy(ServiceName("org.example"), path)
        val call = proxy.createMethodCall(InterfaceName("org.example.Auto"), MethodName("Add"))
        call.append(4)
        call.append(6)

        val reply = proxy.callMethod(call)
        reply.rewind(false)
        assertEquals(10, reply.readInt())

        registration.release()
        assertFailsWith<Error> {
            proxy.callMethod(call)
        }
    }

    @Test
    fun addVTable_supportsAsyncTypedMethodHandlers() {
        val connection = createBusConnection()
        val path = ObjectPath("/org/example/async")
        val obj = createObject(connection, path)
        val registration = obj.addVTable(InterfaceName("org.example.Async")) {
            method(MethodName("Add")) {
                acall { a: Int, b: Int -> a + b }
            }
        }
        try {
            val result = createProxy(ServiceName("org.example"), path).callMethod<Int>(
                InterfaceName("org.example.Async"),
                MethodName("Add")
            ) {
                call(11, 8)
            }
            assertEquals(19, result)
        } finally {
            registration.release()
            obj.release()
        }
    }

    @Test
    fun proxyCallMethodAsync_callbackReceivesTypedResult() {
        val connection = createBusConnection()
        val path = ObjectPath("/org/example/async/callback")
        val obj = createObject(connection, path)
        val registration = obj.addVTable(InterfaceName("org.example.AsyncCallback")) {
            method(MethodName("Add")) {
                call { a: Int, b: Int -> a + b }
            }
        }
        try {
            val proxy = createProxy(ServiceName("org.example"), path)
            val call = proxy.createMethodCall(
                InterfaceName("org.example.AsyncCallback"),
                MethodName("Add")
            )
            call.append(3)
            call.append(4)

            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<Int?>(null)
            val errorRef = AtomicReference<Error?>(null)
            val pending = proxy.callMethodAsync(call) { reply, error ->
                errorRef.set(error)
                if (error == null) {
                    reply.rewind(false)
                    resultRef.set(reply.readInt())
                }
                latch.countDown()
            }

            assertTrue(pending.isPending())
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals(null, errorRef.get())
            assertEquals(7, resultRef.get())
            assertTrue(!pending.isPending())
        } finally {
            registration.release()
            obj.release()
        }
    }

    @Test
    fun proxyCallMethodAsync_suspendReturnsTypedResult() = runBlocking {
        val connection = createBusConnection()
        val path = ObjectPath("/org/example/async/suspend")
        val obj = createObject(connection, path)
        val registration = obj.addVTable(InterfaceName("org.example.AsyncSuspend")) {
            method(MethodName("Mul")) {
                call { a: Int, b: Int -> a * b }
            }
        }
        try {
            val proxy = createProxy(ServiceName("org.example"), path)
            val result = proxy.callMethodAsync<Int>(
                InterfaceName("org.example.AsyncSuspend"),
                MethodName("Mul")
            ) {
                call(6, 7)
            }
            assertEquals(42, result)
        } finally {
            registration.release()
            obj.release()
        }
    }
}
