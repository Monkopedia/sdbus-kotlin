package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmBusType
import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusBackendProvider
import kotlin.time.Duration

internal class JvmConnection(
    internal val backend: com.monkopedia.sdbus.internal.jvmdbus.JvmDbusConnection
) : Connection {
    override fun startEventLoop(): Unit = backend.startEventLoop()

    override suspend fun stopEventLoop(): Unit = backend.stopEventLoop()

    override val currentlyProcessedMessage: Message
        get() = backend.currentlyProcessedMessage()

    override var methodCallTimeout: Duration
        get() = backend.getMethodCallTimeout()
        set(value) = backend.setMethodCallTimeout(value)

    override fun addObjectManager(objectPath: ObjectPath): Resource =
        backend.addObjectManager(objectPath)

    override fun addMatch(match: String, callback: MessageHandler): Resource =
        backend.addMatch(match, callback)

    override fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ): Resource = backend.addMatchAsync(match, callback, installCallback)

    override val uniqueName: BusName get() = backend.uniqueName()

    override fun requestName(name: ServiceName): Unit = backend.requestName(name)

    override fun releaseName(name: ServiceName): Unit = backend.releaseName(name)

    override fun release(): Unit = backend.release()
}

internal actual fun now(): Duration = Duration.ZERO

actual fun createBusConnection(): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(JvmBusType.DEFAULT, null, null, null)
)

actual fun createBusConnection(name: ServiceName): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(
        JvmBusType.DEFAULT,
        null,
        name,
        null
    )
)

actual fun createSystemBusConnection(): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(JvmBusType.SYSTEM, null, null, null)
)

actual fun createSystemBusConnection(name: ServiceName): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(
        JvmBusType.SYSTEM,
        null,
        name,
        null
    )
)

actual fun createSessionBusConnection(): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(JvmBusType.SESSION, null, null, null)
)

actual fun createSessionBusConnection(name: ServiceName): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(
        JvmBusType.SESSION,
        null,
        name,
        null
    )
)

actual fun createSessionBusConnection(address: String): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(
        JvmBusType.SESSION_ADDRESS,
        address,
        null,
        null
    )
)

actual fun createDirectBusConnection(address: String): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(
        JvmBusType.DIRECT_ADDRESS,
        address,
        null,
        null
    )
)

@Deprecated(
    message = "createDirectBusConnection(fd) is native-only and not supported on JVM.",
    level = DeprecationLevel.ERROR
)
actual fun createDirectBusConnection(fd: UnixFd): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(JvmBusType.DIRECT_FD, null, null, fd.fd)
).also { fd.detach() }

@Deprecated(
    message = "createServerBusConnection(fd) is native-only and not supported on JVM.",
    level = DeprecationLevel.ERROR
)
actual fun createServerBusConnection(fd: UnixFd): Connection = JvmConnection(
    JvmDbusBackendProvider.backend.createConnection(JvmBusType.SERVER_FD, null, null, fd.fd)
).also { fd.detach() }
