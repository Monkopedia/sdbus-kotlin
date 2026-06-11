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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PureJavaDbusBackendTest {
    @Test
    fun createProxy_startsEventLoopUnlessDisabled() {
        val fallback = mockk<JvmDbusBackend>(relaxed = true)
        val backend = PureJavaDbusBackend(fallback)
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
        val fallback = mockk<JvmDbusBackend>(relaxed = true)
        val backend = PureJavaDbusBackend(fallback)

        val directError = assertFailsWith<Error> {
            backend.createConnection(JvmBusType.DIRECT_FD, null, null, 11)
        }
        val serverError = assertFailsWith<Error> {
            backend.createConnection(JvmBusType.SERVER_FD, null, null, 12)
        }

        assertTrue(directError.message.orEmpty().contains("createDirectBusConnection(fd)"))
        assertTrue(serverError.message.orEmpty().contains("createServerBusConnection(fd)"))
        verify(exactly = 0) {
            fallback.createConnection(JvmBusType.DIRECT_FD, null, null, 11)
        }
        verify(exactly = 0) {
            fallback.createConnection(JvmBusType.SERVER_FD, null, null, 12)
        }
    }

    @Test
    fun createConnection_usesFallbackWhenEndpointMissing() {
        val fallback = mockk<JvmDbusBackend>()
        val backend = PureJavaDbusBackend(fallback)
        val sessionAddressConnection = mockk<JvmDbusConnection>()
        val directAddressConnection = mockk<JvmDbusConnection>()
        val named = ServiceName("org.example.Named")

        every {
            fallback.createConnection(JvmBusType.SESSION_ADDRESS, null, named, null)
        } returns sessionAddressConnection
        every {
            fallback.createConnection(JvmBusType.DIRECT_ADDRESS, null, null, null)
        } returns directAddressConnection

        val sessionAddressResult =
            backend.createConnection(JvmBusType.SESSION_ADDRESS, null, named, null)
        val directAddressResult =
            backend.createConnection(JvmBusType.DIRECT_ADDRESS, null, null, null)

        assertEquals(sessionAddressConnection, sessionAddressResult)
        assertEquals(directAddressConnection, directAddressResult)
        verify(exactly = 1) {
            fallback.createConnection(JvmBusType.SESSION_ADDRESS, null, named, null)
        }
        verify(exactly = 1) {
            fallback.createConnection(JvmBusType.DIRECT_ADDRESS, null, null, null)
        }
    }
}
