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

    override val uniqueName: BusName get() = backend.uniqueName()

    override fun requestName(name: ServiceName, vararg flags: RequestNameFlag): RequestNameReply =
        backend.requestName(name, flags.fold(0u) { acc, flag -> acc or flag.mask })

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
