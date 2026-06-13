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
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalHandler
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.VTableItem
import kotlin.time.Duration

internal enum class JvmBusType {
    DEFAULT,
    SYSTEM,
    SESSION,
    SESSION_ADDRESS,
    DIRECT_ADDRESS,
    DIRECT_FD,
    SERVER_FD
}

internal interface JvmDbusConnection : Resource {
    fun startEventLoop()
    suspend fun stopEventLoop()
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
        runEventLoopThread: Boolean
    ): JvmDbusProxy

    fun createObject(connection: Connection, objectPath: ObjectPath): JvmDbusObject
}

/**
 * The JVM backend is the self-owned D-Bus connection (epic #93): raw junixsocket transport, our
 * own marshaller and read/dispatch loop — no dbus-java. There is a single implementation
 * ([WireDbusBackend]); the indirection remains so tests can mock the backend boundary.
 */
internal object JvmDbusBackendProvider {
    val backend: JvmDbusBackend by lazy { WireDbusBackend() }
}
