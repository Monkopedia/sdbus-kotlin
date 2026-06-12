package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.BusName
import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PureJavaDbusBackendTest {
    @Test
    fun createProxy_startsEventLoopUnlessDisabled() {
        val backend = PureJavaDbusBackend()
        val connection = mockk<Connection>(relaxed = true)
        val destination = ServiceName("org.example.Proxy")
        val objectPath = ObjectPath("/org/example/proxy")

        every { connection.uniqueName } returns BusName(":1.123")

        backend.createProxy(
            connection = connection,
            destination = destination,
            objectPath = objectPath,
            runEventLoopThread = true
        )
        backend.createProxy(
            connection = connection,
            destination = destination,
            objectPath = objectPath,
            runEventLoopThread = false
        )

        verify(exactly = 1) { connection.startEventLoop() }
    }

    @Test
    fun createConnection_rejectsFdBackedBuses() {
        val backend = PureJavaDbusBackend()

        val directError = assertFailsWith<Error> {
            backend.createConnection(JvmBusType.DIRECT_FD, null, null, 11)
        }
        val serverError = assertFailsWith<Error> {
            backend.createConnection(JvmBusType.SERVER_FD, null, null, 12)
        }

        assertTrue(directError.message.orEmpty().contains("createDirectBusConnection(fd)"))
        assertTrue(serverError.message.orEmpty().contains("createServerBusConnection(fd)"))
    }

    // Native parity (issue #81): an unsatisfiable connection request must throw, never fall
    // back to an in-process stub that fakes success.
    @Test
    fun createConnection_throwsWhenEndpointMissing() {
        val backend = PureJavaDbusBackend()

        val sessionAddressError = assertFailsWith<Error> {
            backend.createConnection(JvmBusType.SESSION_ADDRESS, null, null, null)
        }
        val directAddressError = assertFailsWith<Error> {
            backend.createConnection(JvmBusType.DIRECT_ADDRESS, null, null, null)
        }

        assertTrue(sessionAddressError.message.orEmpty().contains("Failed to open bus"))
        assertTrue(directAddressError.message.orEmpty().contains("Failed to open bus"))
    }
}
