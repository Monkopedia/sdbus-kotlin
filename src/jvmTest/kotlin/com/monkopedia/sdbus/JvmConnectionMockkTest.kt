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
        val message = PlainMessage.createPlainMessage()
        val unique = BusName(":jvm-mock")

        every { backend.enterEventLoopAsync() } just Runs
        coEvery { backend.leaveEventLoop() } just Runs
        every { backend.currentlyProcessedMessage() } returns message
        every { backend.setMethodCallTimeout(timeout) } just Runs
        every { backend.getMethodCallTimeout() } returns timeout
        every { backend.uniqueName() } returns unique
        every { backend.release() } just Runs

        connection.enterEventLoopAsync()
        connection.leaveEventLoop()
        assertEquals(message, connection.currentlyProcessedMessage)
        connection.setMethodCallTimeout(timeout)
        assertEquals(timeout, connection.getMethodCallTimeout())
        assertEquals(unique, connection.getUniqueName())
        connection.release()

        verify(exactly = 1) { backend.enterEventLoopAsync() }
        coVerify(exactly = 1) { backend.leaveEventLoop() }
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
        val installResource = mockk<Resource>()
        val managerResource = mockk<Resource>()
        val objectPath = ObjectPath("/com/example")
        val match = "type='signal'"
        val service = ServiceName("com.example.Test")
        val callback: MessageHandler = {}
        val installCallback: MessageHandler = {}

        every { backend.addObjectManager(objectPath) } returns managerResource
        every { backend.addMatch(match, callback) } returns matchResource
        every { backend.addMatchAsync(match, callback, installCallback) } returns installResource
        every { backend.requestName(service) } just Runs
        every { backend.releaseName(service) } just Runs

        assertEquals(managerResource, connection.addObjectManager(objectPath))
        assertEquals(matchResource, connection.addMatch(match, callback))
        assertEquals(
            installResource,
            connection.addMatchAsync(match, callback, installCallback)
        )
        connection.requestName(service)
        connection.releaseName(service)

        verify(exactly = 1) { backend.addObjectManager(objectPath) }
        verify(exactly = 1) { backend.addMatch(match, callback) }
        verify(exactly = 1) { backend.addMatchAsync(match, callback, installCallback) }
        verify(exactly = 1) { backend.requestName(service) }
        verify(exactly = 1) { backend.releaseName(service) }
    }
}
