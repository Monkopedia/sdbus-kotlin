package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmStaticDispatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JvmMessageSupportTest {
    @Test
    fun methodCallSend_dispatchesViaStaticRegistry() {
        JvmStaticDispatch.register(
            objectPath = "/org/example/send",
            interfaceName = "org.example.Send",
            methodName = "Add",
            argCount = 2
        ) { args ->
            (args[0] as Int) + (args[1] as Int)
        }
        try {
            val call = MethodCall().also {
                it.metadata = Message.Metadata(
                    interfaceName = "org.example.Send",
                    memberName = "Add",
                    path = "/org/example/send",
                    valid = true,
                    empty = false
                )
            }
            call.append(20)
            call.append(22)

            val reply = call.send(Duration.ZERO)

            reply.rewind(false)
            assertEquals(42, reply.readInt())
        } finally {
            JvmStaticDispatch.unregister(
                objectPath = "/org/example/send",
                interfaceName = "org.example.Send",
                methodName = "Add",
                argCount = 2
            )
        }
    }

    @Test
    fun methodCallSend_honorsTimeout() {
        JvmStaticDispatch.register(
            objectPath = "/org/example/send",
            interfaceName = "org.example.Send",
            methodName = "SlowAdd",
            argCount = 2
        ) { args ->
            Thread.sleep(200)
            (args[0] as Int) + (args[1] as Int)
        }
        try {
            val call = MethodCall().also {
                it.metadata = Message.Metadata(
                    interfaceName = "org.example.Send",
                    memberName = "SlowAdd",
                    path = "/org/example/send",
                    valid = true,
                    empty = false
                )
            }
            call.append(20)
            call.append(22)

            val thrown = assertFailsWith<Error> {
                call.send(1.milliseconds)
            }
            assertTrue(thrown.errorMessage.contains("timed out"))
        } finally {
            JvmStaticDispatch.unregister(
                objectPath = "/org/example/send",
                interfaceName = "org.example.Send",
                methodName = "SlowAdd",
                argCount = 2
            )
        }
    }

    @Test
    fun signalSend_emitsThroughBoundObjectEmitter() {
        val service = ServiceName("org.example.jvm.signal.send")
        val path = ObjectPath("/org/example/jvm/signal/send")
        val iface = InterfaceName("org.example.jvm.Signal")
        val signalName = SignalName("Changed")
        val serverConnection = createBusConnection(service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, path)
        val proxy = createProxy(proxyConnection, service, path, runEventLoopThread = false)
        val latch = CountDownLatch(1)
        val value = arrayOf<Int?>(null)
        val registration = proxy.registerSignalHandler(iface, signalName) { message ->
            message.rewind(false)
            value[0] = message.readInt()
            latch.countDown()
        }

        try {
            val signal = obj.createSignal(iface, signalName)
            signal.append(42)
            signal.send()

            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals(42, value[0])
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun signalSend_withoutBoundEmitterFailsWithSdbusError() {
        val signal = Signal().also {
            it.metadata = Message.Metadata(
                interfaceName = "org.example.jvm.Signal",
                memberName = "Changed",
                path = "/org/example/jvm/signal",
                valid = true,
                empty = true
            )
        }

        assertFailsWith<Error> {
            signal.send()
        }
    }

    @Test
    fun credentialAccessors_doNotUseUnsupportedOperationExceptions() {
        val message = PlainMessage.createPlainMessage()

        val failures = listOf(
            runCatching { message.credsPid }.exceptionOrNull(),
            runCatching { message.credsUid }.exceptionOrNull(),
            runCatching { message.credsEuid }.exceptionOrNull(),
            runCatching { message.credsGid }.exceptionOrNull(),
            runCatching { message.credsEgid }.exceptionOrNull(),
            runCatching { message.credsSupplementaryGids }.exceptionOrNull(),
            runCatching { message.seLinuxContext }.exceptionOrNull()
        )

        failures.forEach { failure ->
            assertFalse(failure is UnsupportedOperationException)
        }
    }

    @Test
    fun credentialAccessors_withoutSenderCredentialsFailWithSdbusError() {
        val message = PlainMessage.createPlainMessage()

        assertFailsWith<Error> { message.credsPid }
        assertFailsWith<Error> { message.credsUid }
        assertFailsWith<Error> { message.credsEuid }
        assertFailsWith<Error> { message.credsGid }
        assertFailsWith<Error> { message.credsEgid }
        assertFailsWith<Error> { message.credsSupplementaryGids }
        assertFailsWith<Error> { message.seLinuxContext }
    }

    // Regression: D-Bus `ay` (e.g. a GATT characteristic value) arrives from dbus-java as
    // signed java.lang.Byte values, but the declared type is List<UByte>. Deserialization
    // must box each element into UByte; otherwise reading it throws
    // "class java.lang.Byte cannot be cast to class kotlin.UByte".
    @Test
    fun deserialize_ayByteArray_coercesSignedBytesToUByteList() {
        val message = PlainMessage.createPlainMessage()
        message.payload.add(listOf<Byte>(0xBF.toByte(), 0x01, 0x02))

        val result = message.deserialize<List<UByte>>()

        assertEquals(listOf<UByte>(0xBFu, 0x01u, 0x02u), result)
    }
}
