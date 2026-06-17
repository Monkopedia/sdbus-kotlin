package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusConnection
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class JvmConnectionMockkTest {
    @Test
    fun delegatesLifecycleAndTimeoutCallsToBackend() = runTest {
        val backend = mockk<JvmDbusConnection>()
        val connection = JvmConnection(backend)
        val timeout = 5.seconds
        val message = createPlainMessage()
        val unique = BusName(":jvm-mock")

        every { backend.startEventLoop() } just Runs
        coEvery { backend.stopEventLoop() } just Runs
        every { backend.currentlyProcessedMessage() } returns message
        every { backend.setMethodCallTimeout(timeout) } just Runs
        every { backend.getMethodCallTimeout() } returns timeout
        every { backend.uniqueName() } returns unique
        every { backend.release() } just Runs

        connection.startEventLoop()
        connection.stopEventLoop()
        assertEquals(message, connection.currentlyProcessedMessage)
        connection.methodCallTimeout = timeout
        assertEquals(timeout, connection.methodCallTimeout)
        assertEquals(unique, connection.uniqueName)
        connection.release()

        verify(exactly = 1) { backend.startEventLoop() }
        coVerify(exactly = 1) { backend.stopEventLoop() }
        verify(exactly = 1) { backend.currentlyProcessedMessage() }
        verify(exactly = 1) { backend.setMethodCallTimeout(timeout) }
        verify(exactly = 1) { backend.getMethodCallTimeout() }
        verify(exactly = 1) { backend.uniqueName() }
        verify(exactly = 1) { backend.release() }
    }

    @Test
    fun delegatesMatchAndNameCallsToBackend() {
        val backend = mockk<JvmDbusConnection>()
        val connection = JvmConnection(backend)
        val matchResource = mockk<Resource>()
        val managerResource = mockk<Resource>()
        val objectPath = ObjectPath("/com/example")
        val match = "type='signal'"
        val service = ServiceName("com.example.Test")
        val callback: MessageHandler = {}

        every { backend.addObjectManager(objectPath) } returns managerResource
        every { backend.addMatch(match, callback) } returns matchResource
        every { backend.requestName(service) } just Runs
        every { backend.releaseName(service) } just Runs

        assertEquals(managerResource, connection.addObjectManager(objectPath))
        assertEquals(matchResource, connection.addMatch(match, callback))
        connection.requestName(service)
        connection.releaseName(service)

        verify(exactly = 1) { backend.addObjectManager(objectPath) }
        verify(exactly = 1) { backend.addMatch(match, callback) }
        verify(exactly = 1) { backend.requestName(service) }
        verify(exactly = 1) { backend.releaseName(service) }
    }
}
