package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmBusType
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusBackend
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusBackendProvider
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusConnection
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusObject
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusProxy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration

class JvmFactoryMockkTest {
    private fun mockConnectionBackend(): JvmDbusConnection {
        val connection = mockk<JvmDbusConnection>(relaxed = true)
        every { connection.currentlyProcessedMessage() } returns PlainMessage.createPlainMessage()
        every { connection.getMethodCallTimeout() } returns Duration.ZERO
        every { connection.uniqueName() } returns BusName(":jvm-factory")
        return connection
    }

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun createConnectionFactories_delegateExpectedBusTypeAndEndpoint() {
        val backend = mockk<JvmDbusBackend>()
        val connectionBackend = mockConnectionBackend()
        val serviceName = ServiceName("org.example.Factory")

        mockkObject(JvmDbusBackendProvider)
        try {
            every { JvmDbusBackendProvider.backend } returns backend
            every {
                backend.createConnection(any(), any(), any(), any())
            } returns connectionBackend

            createBusConnection()
            createBusConnection(serviceName)
            createSystemBusConnection()
            createSystemBusConnection(serviceName)
            createSessionBusConnection()
            createSessionBusConnection(serviceName)
            createSessionBusConnectionWithAddress("unix:path=/tmp/session")
            createRemoteSystemBusConnection("example-host")
            createDirectBusConnection("unix:path=/tmp/direct")
            createDirectBusConnection(42)
            createServerBus(99)

            verify(exactly = 1) {
                backend.createConnection(JvmBusType.DEFAULT, null, null, null)
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.DEFAULT, null, serviceName, null)
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.SYSTEM, null, null, null)
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.SYSTEM, null, serviceName, null)
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.SESSION, null, null, null)
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.SESSION, null, serviceName, null)
            }
            verify(exactly = 1) {
                backend.createConnection(
                    JvmBusType.SESSION_ADDRESS,
                    "unix:path=/tmp/session",
                    null,
                    null
                )
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.REMOTE_SYSTEM, "example-host", null, null)
            }
            verify(exactly = 1) {
                backend.createConnection(
                    JvmBusType.DIRECT_ADDRESS,
                    "unix:path=/tmp/direct",
                    null,
                    null
                )
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.DIRECT_FD, null, null, 42)
            }
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.SERVER_FD, null, null, 99)
            }
        } finally {
            unmockkObject(JvmDbusBackendProvider)
        }
    }

    @Test
    fun createProxyFactory_delegatesToBackendWithExplicitConnection() {
        val backend = mockk<JvmDbusBackend>()
        val connectionBackend = mockConnectionBackend()
        val proxyBackend = mockk<JvmDbusProxy>(relaxed = true)
        val connection = JvmConnection(connectionBackend)
        val destination = ServiceName("org.example.Proxy")
        val objectPath = ObjectPath("/org/example/proxy")

        mockkObject(JvmDbusBackendProvider)
        try {
            every { JvmDbusBackendProvider.backend } returns backend
            every {
                backend.createProxy(connection, destination, objectPath, true)
            } returns proxyBackend

            val proxy = createProxy(
                connection,
                destination,
                objectPath,
                dontRunEventLoopThread = true
            )

            assertEquals(objectPath, proxy.objectPath)
            assertEquals(connection, proxy.connection)
            verify(exactly = 1) {
                backend.createProxy(connection, destination, objectPath, true)
            }
        } finally {
            unmockkObject(JvmDbusBackendProvider)
        }
    }

    @Test
    fun createProxyFactory_defaultOverloadCreatesDefaultConnectionThenProxy() {
        val backend = mockk<JvmDbusBackend>()
        val connectionBackend = mockConnectionBackend()
        val proxyBackend = mockk<JvmDbusProxy>(relaxed = true)
        val destination = ServiceName("org.example.Proxy.Default")
        val objectPath = ObjectPath("/org/example/proxy/default")
        val createdConnection = slot<Connection>()

        mockkObject(JvmDbusBackendProvider)
        try {
            every { JvmDbusBackendProvider.backend } returns backend
            every {
                backend.createConnection(JvmBusType.DEFAULT, null, null, null)
            } returns connectionBackend
            every {
                backend.createProxy(capture(createdConnection), destination, objectPath, false)
            } returns proxyBackend

            val proxy = createProxy(destination, objectPath)

            assertEquals(objectPath, proxy.objectPath)
            val wrapped = createdConnection.captured as JvmConnection
            assertEquals(connectionBackend, wrapped.backend)
            verify(exactly = 1) {
                backend.createConnection(JvmBusType.DEFAULT, null, null, null)
            }
            verify(exactly = 1) {
                backend.createProxy(any(), destination, objectPath, false)
            }
        } finally {
            unmockkObject(JvmDbusBackendProvider)
        }
    }

    @Test
    fun createObjectFactory_delegatesToBackend() {
        val backend = mockk<JvmDbusBackend>()
        val connectionBackend = mockConnectionBackend()
        val objectBackend = mockk<JvmDbusObject>(relaxed = true)
        val connection = JvmConnection(connectionBackend)
        val objectPath = ObjectPath("/org/example/object")

        mockkObject(JvmDbusBackendProvider)
        try {
            every { JvmDbusBackendProvider.backend } returns backend
            every { backend.createObject(connection, objectPath) } returns objectBackend

            val obj = createObject(connection, objectPath)

            assertEquals(objectPath, obj.objectPath)
            assertEquals(connection, obj.connection)
            verify(exactly = 1) { backend.createObject(connection, objectPath) }
        } finally {
            unmockkObject(JvmDbusBackendProvider)
        }
    }

    @Test
    fun createConnectionFactory_propagatesBackendFailures() {
        val backend = mockk<JvmDbusBackend>()

        mockkObject(JvmDbusBackendProvider)
        try {
            every { JvmDbusBackendProvider.backend } returns backend
            every {
                backend.createConnection(JvmBusType.DEFAULT, null, null, null)
            } throws IllegalStateException("boom")

            val error = assertFailsWith<IllegalStateException> {
                createBusConnection()
            }
            assertEquals("boom", error.message)
        } finally {
            unmockkObject(JvmDbusBackendProvider)
        }
    }

    @Test
    fun createProxyAndObjectFactories_propagateBackendFailures() {
        val backend = mockk<JvmDbusBackend>()
        val connectionBackend = mockConnectionBackend()
        val connection = JvmConnection(connectionBackend)
        val destination = ServiceName("org.example.Failure")
        val path = ObjectPath("/org/example/failure")

        mockkObject(JvmDbusBackendProvider)
        try {
            every { JvmDbusBackendProvider.backend } returns backend
            every {
                backend.createProxy(connection, destination, path, false)
            } throws IllegalArgumentException("proxy failed")
            every {
                backend.createObject(connection, path)
            } throws IllegalStateException("object failed")

            assertEquals(
                "proxy failed",
                assertFailsWith<IllegalArgumentException> {
                    createProxy(connection, destination, path)
                }.message
            )
            assertEquals(
                "object failed",
                assertFailsWith<IllegalStateException> {
                    createObject(connection, path)
                }.message
            )
        } finally {
            unmockkObject(JvmDbusBackendProvider)
        }
    }
}
