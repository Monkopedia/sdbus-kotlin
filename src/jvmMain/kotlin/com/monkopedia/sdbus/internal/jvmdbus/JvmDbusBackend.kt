package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.AsyncReplyHandler
import com.monkopedia.sdbus.BusName
import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MessageHandler
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.NoOpResource
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalHandler
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.VTableItem
import com.monkopedia.sdbus.createError
import com.monkopedia.sdbus.signalFromMetadata
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration

internal enum class JvmBusType {
    DEFAULT,
    SYSTEM,
    SESSION,
    SESSION_ADDRESS,
    REMOTE_SYSTEM,
    DIRECT_ADDRESS,
    DIRECT_FD,
    SERVER_FD
}

internal interface JvmDbusConnection : Resource {
    fun enterEventLoopAsync()
    suspend fun leaveEventLoop()
    fun currentlyProcessedMessage(): Message
    fun setMethodCallTimeout(timeout: Duration)
    fun getMethodCallTimeout(): Duration
    fun addObjectManager(objectPath: ObjectPath): Resource
    fun addMatch(match: String, callback: MessageHandler): Resource
    fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ): Resource

    fun uniqueName(): BusName
    fun requestName(name: ServiceName)
    fun releaseName(name: ServiceName)
}

internal interface JvmDbusProxy : Resource {
    fun currentlyProcessedMessage(): Message
    fun createMethodCall(interfaceName: InterfaceName, methodName: MethodName): MethodCall
    fun callMethod(message: MethodCall): MethodReply
    fun callMethod(message: MethodCall, timeout: ULong): MethodReply
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): PendingAsyncCall

    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall

    suspend fun callMethodAsync(message: MethodCall): MethodReply
    suspend fun callMethodAsync(message: MethodCall, timeout: ULong): MethodReply
    fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource
}

internal interface JvmDbusObject : Resource {
    fun emitPropertiesChangedSignal(interfaceName: InterfaceName, propNames: List<PropertyName>)
    fun emitPropertiesChangedSignal(interfaceName: InterfaceName)
    fun emitInterfacesAddedSignal()
    fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>)
    fun emitInterfacesRemovedSignal()
    fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>)
    fun addObjectManager(): Resource
    fun currentlyProcessedMessage(): Message
    fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): Resource
    fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal
    fun emitSignal(message: Signal)
}

internal interface JvmDbusBackend {
    fun createConnection(
        busType: JvmBusType,
        endpoint: String?,
        name: ServiceName?,
        fd: Int?
    ): JvmDbusConnection

    fun createProxy(
        connection: Connection,
        destination: ServiceName,
        objectPath: ObjectPath,
        dontRunEventLoopThread: Boolean
    ): JvmDbusProxy

    fun createObject(connection: Connection, objectPath: ObjectPath): JvmDbusObject
}

internal object JvmDbusBackendProvider {
    val backend: JvmDbusBackend = PureJavaDbusBackend(StubJvmDbusBackend())
}

internal class StubJvmDbusBackend : JvmDbusBackend {
    override fun createConnection(
        busType: JvmBusType,
        endpoint: String?,
        name: ServiceName?,
        fd: Int?
    ): JvmDbusConnection = StubJvmDbusConnection(name)

    override fun createProxy(
        connection: Connection,
        destination: ServiceName,
        objectPath: ObjectPath,
        dontRunEventLoopThread: Boolean
    ): JvmDbusProxy {
        if (!dontRunEventLoopThread) {
            connection.enterEventLoopAsync()
        }
        return StubJvmDbusProxy(
            destination = destination,
            objectPath = objectPath
        )
    }

    override fun createObject(connection: Connection, objectPath: ObjectPath): JvmDbusObject =
        StubJvmDbusObject(
            objectPath = objectPath,
            senderName = runCatching { connection.getUniqueName().value }.getOrNull()
        )
}

private class StubJvmDbusConnection(private val configuredName: ServiceName?) : JvmDbusConnection {
    override fun enterEventLoopAsync(): Unit = Unit

    override suspend fun leaveEventLoop(): Unit = Unit

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: com.monkopedia.sdbus.PlainMessage.createPlainMessage()

    override fun setMethodCallTimeout(timeout: Duration): Unit = Unit

    override fun getMethodCallTimeout(): Duration = Duration.ZERO

    override fun addObjectManager(objectPath: ObjectPath): Resource = NoOpResource

    override fun addMatch(match: String, callback: MessageHandler): Resource =
        LocalJvmMatchBus.register(match, callback)

    override fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ): Resource = addMatch(match, callback).also {
        installCallback(signalFromMetadata(Message.Metadata(valid = true, empty = true)))
    }

    override fun uniqueName(): BusName = configuredName ?: BusName(":jvm-stub")

    override fun requestName(name: ServiceName): Unit = Unit

    override fun releaseName(name: ServiceName): Unit = Unit

    override fun release(): Unit = Unit
}

private class StubJvmDbusProxy(
    private val destination: ServiceName,
    private val objectPath: ObjectPath
) : JvmDbusProxy {
    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: com.monkopedia.sdbus.PlainMessage.createPlainMessage()

    private fun invalidReply(path: String, interfaceName: String, methodName: String): MethodReply =
        com.monkopedia.sdbus.MethodReply().also {
            it.metadata = Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = destination.value,
                path = path,
                valid = false,
                empty = true
            )
        }

    override fun createMethodCall(
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall = com.monkopedia.sdbus.MethodCall().also {
        it.metadata = Message.Metadata(
            interfaceName = interfaceName.value,
            memberName = methodName.value,
            destination = destination.value,
            path = objectPath.value,
            valid = true,
            empty = true
        )
    }

    override fun callMethod(message: MethodCall): MethodReply = message.send(0u)

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply =
        message.send(timeout)

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): PendingAsyncCall = callMethodAsync(message, asyncReplyCallback, 0u)

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall {
        val cancelled = AtomicBoolean(false)
        val pending = AtomicBoolean(true)
        val interfaceName = message.getInterfaceName().orEmpty()
        val methodName = message.getMemberName().orEmpty()
        val path = message.getPath() ?: objectPath.value
        thread(
            start = true,
            isDaemon = true,
            name = "sdbus-jvm-stub-call-$interfaceName.$methodName"
        ) {
            val outcome = runCatching {
                callMethod(message, timeout)
            }
            pending.set(false)
            if (cancelled.get()) return@thread
            outcome.fold(
                onSuccess = { asyncReplyCallback(it, null) },
                onFailure = {
                    val error = it as? com.monkopedia.sdbus.Error
                        ?: createError(-1, it.message ?: "JVM stub async call failed")
                    asyncReplyCallback(invalidReply(path, interfaceName, methodName), error)
                }
            )
        }
        return PendingAsyncCall(
            cancelAction = { cancelled.set(true) },
            isPendingAction = { pending.get() }
        )
    }

    override suspend fun callMethodAsync(message: MethodCall): MethodReply = callMethod(message, 0u)

    override suspend fun callMethodAsync(message: MethodCall, timeout: ULong): MethodReply =
        callMethod(message, timeout)

    override fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource {
        val match = "sender='${destination.value}'," +
            "path='${objectPath.value}'," +
            "interface='${interfaceName.value}'," +
            "member='${signalName.value}'"
        return LocalJvmMatchBus.register(match) { message ->
            signalHandler(message as Signal)
        }
    }

    override fun release() = Unit
}

private class StubJvmDbusObject(
    private val objectPath: ObjectPath,
    private val senderName: String?
) : JvmDbusObject {
    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ): Unit = Unit

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName): Unit = Unit

    override fun emitInterfacesAddedSignal(): Unit = Unit

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>): Unit = Unit

    override fun emitInterfacesRemovedSignal(): Unit = Unit

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>): Unit = Unit

    override fun addObjectManager(): Resource = NoOpResource

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: com.monkopedia.sdbus.PlainMessage.createPlainMessage()

    override fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): Resource =
        NoOpResource

    override fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal =
        signalFromMetadata(
            Message.Metadata(
                interfaceName = interfaceName.value,
                memberName = signalName.value,
                sender = senderName,
                path = objectPath.value,
                valid = true,
                empty = true
            )
        ) { emitSignal(it) }

    override fun emitSignal(message: Signal) {
        val interfaceName = message.getInterfaceName()
            ?: throw createError(-1, "emitSignal failed: missing interface name")
        val signalName = message.getMemberName()
            ?: throw createError(-1, "emitSignal failed: missing signal name")
        val path = message.getPath() ?: objectPath.value
        LocalJvmMatchBus.emit(
            sender = message.getSender() ?: senderName,
            path = path,
            interfaceName = interfaceName,
            member = signalName,
            payload = message.payload
        )
    }

    override fun release() = Unit
}
