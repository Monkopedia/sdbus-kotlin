package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusBackendProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

internal class JvmProxy(
    override val connection: Connection,
    override val objectPath: ObjectPath,
    private val backend: com.monkopedia.sdbus.internal.jvmdbus.JvmDbusProxy
) : Proxy,
    CallbackAsyncProxy {
    override val currentlyProcessedMessage: Message
        get() = backend.currentlyProcessedMessage()

    override fun createMethodCall(
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall = backend.createMethodCall(interfaceName, methodName)

    override fun callMethod(message: MethodCall): MethodReply = backend.callMethod(message)

    override fun callMethod(message: MethodCall, timeout: Duration): MethodReply =
        backend.callMethod(message, timeout.inWholeMicroseconds.toULong())

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): PendingAsyncCall = backend.callMethodAsync(message, asyncReplyCallback)

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall = backend.callMethodAsync(message, asyncReplyCallback, timeout)

    override suspend fun callMethodAsync(message: MethodCall): MethodReply =
        backend.callMethodAsync(message)

    override suspend fun callMethodAsync(message: MethodCall, timeout: Duration): MethodReply =
        backend.callMethodAsync(message, timeout.inWholeMicroseconds.toULong())

    override fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource = backend.registerSignalHandler(interfaceName, signalName, signalHandler)

    override fun release(): Unit = backend.release()
}

internal actual class PendingAsyncCall internal constructor(
    private val cancelAction: () -> Unit = {},
    private val isPendingAction: () -> Boolean = { false }
) : Resource {
    private val released = AtomicBoolean(false)

    actual override fun release() {
        if (released.compareAndSet(false, true)) {
            cancelAction()
        }
    }

    actual fun isPending(): Boolean = !released.get() && isPendingAction()
}

actual fun createProxy(
    connection: Connection,
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean
): Proxy = JvmProxy(
    connection,
    objectPath,
    JvmDbusBackendProvider.backend.createProxy(
        connection,
        destination,
        objectPath,
        dontRunEventLoopThread
    )
)

actual fun createProxy(
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean
): Proxy = createProxy(
    createBusConnection(),
    destination,
    objectPath,
    dontRunEventLoopThread
)
