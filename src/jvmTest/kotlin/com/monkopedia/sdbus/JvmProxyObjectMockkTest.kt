package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusObject
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusProxy
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class JvmProxyObjectMockkTest {
    @Test
    fun proxyDelegatesSyncAndSuspendAsyncCalls() = runTest {
        val backend = mockk<JvmDbusProxy>()
        val connection = mockk<Connection>()
        val objectPath = ObjectPath("/com/example")
        val proxy = JvmProxy(connection, objectPath, backend)
        val call = MethodCall()
        val reply = MethodReply()
        val resource = mockk<Resource>()
        val signalHandler: SignalHandler = {}
        val callback: AsyncReplyHandler = { _, _ -> }
        val currentMessage = PlainMessage.createPlainMessage()

        every { backend.currentlyProcessedMessage() } returns currentMessage
        every { backend.createMethodCall(InterfaceName("com.example"), MethodName("Ping")) } returns
            call
        every { backend.callMethod(call) } returns reply
        every { backend.callMethod(call, 42u) } returns reply
        every { backend.callMethodAsync(call, callback) } returns PendingAsyncCall()
        every { backend.callMethodAsync(call, callback, 42u) } returns PendingAsyncCall()
        coEvery { backend.callMethodAsync(call) } returns reply
        coEvery { backend.callMethodAsync(call, 42u) } returns reply
        every {
            backend.registerSignalHandler(
                InterfaceName("com.example"),
                SignalName("Changed"),
                signalHandler
            )
        } returns resource
        every { backend.release() } just Runs

        assertEquals(currentMessage, proxy.currentlyProcessedMessage)
        assertEquals(call, proxy.createMethodCall(InterfaceName("com.example"), MethodName("Ping")))
        assertEquals(reply, proxy.callMethod(call))
        assertEquals(reply, proxy.callMethod(call, 42u))
        assertEquals(reply, proxy.callMethodAsync(call))
        assertEquals(reply, proxy.callMethodAsync(call, 42u))
        assertEquals(
            resource,
            proxy.registerSignalHandler(
                InterfaceName("com.example"),
                SignalName("Changed"),
                signalHandler
            )
        )
        proxy.callMethodAsync(call, callback)
        proxy.callMethodAsync(call, callback, 42u)
        proxy.release()

        verify(exactly = 1) { backend.currentlyProcessedMessage() }
        verify(exactly = 1) {
            backend.createMethodCall(InterfaceName("com.example"), MethodName("Ping"))
        }
        verify(exactly = 1) { backend.callMethod(call) }
        verify(exactly = 1) { backend.callMethod(call, 42u) }
        verify(exactly = 1) { backend.callMethodAsync(call, callback) }
        verify(exactly = 1) { backend.callMethodAsync(call, callback, 42u) }
        coVerify(exactly = 1) { backend.callMethodAsync(call) }
        coVerify(exactly = 1) { backend.callMethodAsync(call, 42u) }
        verify(exactly = 1) {
            backend.registerSignalHandler(
                InterfaceName("com.example"),
                SignalName("Changed"),
                signalHandler
            )
        }
        verify(exactly = 1) { backend.release() }
    }

    @Test
    fun objectDelegatesLifecycleAndSignalOperations() {
        val backend = mockk<JvmDbusObject>()
        val connection = mockk<Connection>()
        val objectPath = ObjectPath("/com/example/object")
        val sdbusObject = JvmObject(connection, objectPath, backend)
        val managerResource = mockk<Resource>()
        val vtableResource = mockk<Resource>()
        val signal = Signal()
        val message = PlainMessage.createPlainMessage()
        val interfaceName = InterfaceName("com.example.Object")

        every {
            backend.emitPropertiesChangedSignal(interfaceName, listOf(PropertyName("one")))
        } just
            Runs
        every { backend.emitPropertiesChangedSignal(interfaceName) } just Runs
        every { backend.emitInterfacesAddedSignal() } just Runs
        every { backend.emitInterfacesAddedSignal(listOf(interfaceName)) } just Runs
        every { backend.emitInterfacesRemovedSignal() } just Runs
        every { backend.emitInterfacesRemovedSignal(listOf(interfaceName)) } just Runs
        every { backend.addObjectManager() } returns managerResource
        every { backend.currentlyProcessedMessage() } returns message
        every { backend.addVTable(interfaceName, emptyList<VTableItem>()) } returns vtableResource
        every { backend.createSignal(interfaceName, SignalName("Ping")) } returns signal
        every { backend.emitSignal(signal) } just Runs
        every { backend.release() } just Runs

        sdbusObject.emitPropertiesChangedSignal(interfaceName, listOf(PropertyName("one")))
        sdbusObject.emitPropertiesChangedSignal(interfaceName)
        sdbusObject.emitInterfacesAddedSignal()
        sdbusObject.emitInterfacesAddedSignal(listOf(interfaceName))
        sdbusObject.emitInterfacesRemovedSignal()
        sdbusObject.emitInterfacesRemovedSignal(listOf(interfaceName))
        assertEquals(managerResource, sdbusObject.addObjectManager())
        assertEquals(message, sdbusObject.currentlyProcessedMessage)
        assertEquals(vtableResource, sdbusObject.addVTable(interfaceName, emptyList<VTableItem>()))
        assertEquals(signal, sdbusObject.createSignal(interfaceName, SignalName("Ping")))
        sdbusObject.emitSignal(signal)
        sdbusObject.release()

        verify(exactly = 1) {
            backend.emitPropertiesChangedSignal(interfaceName, listOf(PropertyName("one")))
        }
        verify(exactly = 1) { backend.emitPropertiesChangedSignal(interfaceName) }
        verify(exactly = 1) { backend.emitInterfacesAddedSignal() }
        verify(exactly = 1) { backend.emitInterfacesAddedSignal(listOf(interfaceName)) }
        verify(exactly = 1) { backend.emitInterfacesRemovedSignal() }
        verify(exactly = 1) { backend.emitInterfacesRemovedSignal(listOf(interfaceName)) }
        verify(exactly = 1) { backend.addObjectManager() }
        verify(exactly = 1) { backend.currentlyProcessedMessage() }
        verify(exactly = 1) { backend.addVTable(interfaceName, emptyList<VTableItem>()) }
        verify(exactly = 1) { backend.createSignal(interfaceName, SignalName("Ping")) }
        verify(exactly = 1) { backend.emitSignal(signal) }
        verify(exactly = 1) { backend.release() }
    }
}
