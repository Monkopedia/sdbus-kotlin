@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import kotlin.time.Duration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

class VTableAdder(private val object_: IObject, private val vtable: List<VTableItem>) {
    fun forInterface(interfaceName: InterfaceName) {
        object_.addVTable(interfaceName, vtable)
    }

    fun forInterface(interfaceName: String) {
        forInterface(InterfaceName(interfaceName))
    }
}

class SignalEmitter {
    private var signal_: Signal? = null
    private val object_: IObject
    private val signalName_: String

    constructor(obj: IObject, signalName: String) {
        this.object_ = obj
        this.signalName_ = signalName
    }

    constructor(obj: IObject, signalName: SignalName) : this(obj, signalName.value)

    fun onInterface(interfaceName: InterfaceName): SignalEmitter = onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): SignalEmitter = apply {
        signal_ = object_.createSignal(interfaceName, signalName_)
    }

    inline fun emit(builder: TypedArgumentsBuilder) = emit(build(builder))

    fun emit(typedArgs: TypedArguments) {
        require(signal_ != null)
        signal_!!.serialize(typedArgs)
        object_.emitSignal(signal_!!)
    }
}

class MethodInvoker {
    private var timeout_: ULong = 0u
    private var method_: MethodCall? = null
    private val proxy: IProxy
    private val methodName: String
    private var hasCalled = false
    private var exception: Throwable? = null

    constructor(proxy: IProxy, methodName: String) {
        this.proxy = proxy
        this.methodName = methodName
    }

    constructor(proxy: IProxy, methodName: MethodName) : this(proxy, methodName.value)

    fun onInterface(interfaceName: InterfaceName): MethodInvoker = onInterface(interfaceName.value)
    fun onInterface(interfaceName: String): MethodInvoker = apply {
        method_ = proxy.createMethodCall(interfaceName, methodName)
    }

    fun withTimeout(usec: ULong): MethodInvoker = apply {
        timeout_ = usec
    }

    inline fun withTimeout(timeout: Duration): MethodInvoker =
        withTimeout(timeout.inWholeMicroseconds.toULong())

    inline fun withArguments(builder: TypedArgumentsBuilder): MethodInvoker =
        withArguments(build(builder))

    fun withArguments(typedArgs: TypedArguments): MethodInvoker = apply {
        require(method_?.isValid == true)
        method_!!.serialize(typedArgs)
    }

    inline fun <reified T : Any> readResult(): T {
        val serializer = serializer<T>()
        return readResult(serializer, serializersModuleOf(serializer)) as T
    }

    fun <T : Any> readResult(serializer: DeserializationStrategy<T>, module: SerializersModule): T {
        try {
            require(method_?.isValid == true)
            val reply = proxy.callMethod(method_!!, timeout_)
            hasCalled = true

            return reply.deserialize(serializer, module)
        } catch (t: Throwable) {
            exception = t
            throw t
        }
    }

    fun dontExpectReply() {
        require(method_?.isValid == true)

        method_!!.dontExpectReply = true
        if (hasCalled || exception != null) {
            return
        }
        memScoped {
            try {
                require(method_?.isValid == true)
                proxy.callMethod(method_!!, timeout_)
                hasCalled = true
            } catch (t: Throwable) {
                exception = t
                throw t
            }
        }
    }
}

class AsyncMethodInvoker {
    private var timeout_: ULong = 0u
    private var method_: MethodCall? = null
    private val proxy: IProxy
    private val methodName: String

    constructor(proxy: IProxy, methodName: String) {
        this.proxy = proxy
        this.methodName = methodName
    }

    constructor(proxy: IProxy, methodName: MethodName) : this(proxy, methodName.value)

    fun onInterface(interfaceName: InterfaceName): AsyncMethodInvoker =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncMethodInvoker = apply {
        method_ = proxy.createMethodCall(interfaceName, methodName)
    }

    fun withTimeout(usec: ULong): AsyncMethodInvoker = apply {
        timeout_ = usec
    }

    inline fun withTimeout(timeout: Duration): AsyncMethodInvoker =
        withTimeout(timeout.inWholeMicroseconds.toULong())

    inline fun withArguments(builder: TypedArgumentsBuilder): AsyncMethodInvoker =
        withArguments(build(builder))

    fun withArguments(typedArgs: TypedArguments): AsyncMethodInvoker = apply {
        require(method_?.isValid == true)

        method_!!.serialize(typedArgs)
    }

    fun uponReplyInvoke(callback: TypedMethodCall<*>): PendingAsyncCall {
        require(method_?.isValid == true)

        return proxy.callMethodAsync(method_!!, makeAsyncReplyHandler(callback), timeout_)
    }

    inline fun uponReplyInvoke(crossinline callbackBuilder: TypedMethodBuilder): PendingAsyncCall =
        uponReplyInvoke(build(callbackBuilder))

    suspend inline fun <reified T> getResult(): T {
        val completable = CompletableDeferred<T>()

        uponReplyInvoke {
            call { result: T ->
                completable.complete(result)
            } onError { error ->
                completable.completeExceptionally(error ?: Throwable("Unexpected failure"))
            }
        }

        return completable.await()
    }

    fun makeAsyncReplyHandler(callback: TypedMethodCall<*>): AsyncReplyHandler = { reply, error ->
        if (error != null) {
            callback.invoke(error)
        } else {
            callback.invoke(reply)
        }
    }
}

class SignalSubscriber(private val proxy: IProxy, private val signalName: String) {
    private var interfaceName_: String? = null

    constructor(proxy: IProxy, signalName: SignalName) : this(proxy, signalName.value)

    fun onInterface(interfaceName: InterfaceName): SignalSubscriber =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): SignalSubscriber = apply {
        interfaceName_ = interfaceName
    }

    inline fun call(builder: TypedMethodBuilder): Resource = call(build(builder))
    fun call(callback: TypedMethodCall<*>): Resource {
        require(interfaceName_ != null)

        return proxy.registerSignalHandler(
            interfaceName_!!,
            signalName,
            makeSignalHandler(callback)
        )
    }

    fun makeSignalHandler(callback: TypedMethodCall<*>): SignalHandler = { signal ->
        callback.invoke(signal)
    }
}

class PropertyGetter(private val proxy: IProxy, private val propertyName: String) {

    inline fun <reified T : Any> onInterface(interfaceName: InterfaceName): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return onInterface(
            interfaceName.value,
            serializer,
            module,
            signatureOf<T>()
        )
    }

    inline fun <reified T : Any> onInterface(interfaceName: String): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return onInterface(
            interfaceName,
            serializer,
            module,
            signatureOf<T>()
        )
    }

    fun <T : Any> onInterface(
        interfaceName: String,
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T {
        memScoped {
            return proxy.callMethod("Get")
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments {
                    call(interfaceName, propertyName)
                }
                .readResult<Variant>()
                .get(serializer, module, signature)
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AsyncPropertyGetter(private val proxy: IProxy, private val propertyName: String) {

    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertyGetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncPropertyGetter = apply {
        interfaceName_ = interfaceName
    }

    inline fun uponReplyInvoke(crossinline callbackBuilder: TypedMethodBuilder) =
        uponReplyInvoke(build(callbackBuilder))

    fun uponReplyInvoke(callback: TypedMethodCall<*>): PendingAsyncCall {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("Get")
        return shared
            .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName) }
            .uponReplyInvoke(callback)
    }

    suspend inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return get(serializer, module, signatureOf<T>())
    }

    suspend fun <T : Any> get(
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T {
        memScoped {
            require(interfaceName_?.isNotEmpty() == true)
            return proxy.callMethodAsync("Get")
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName_!!, propertyName) }
                .getResult<Variant>()
                .get(serializer, module, signature)
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class PropertySetter(private val proxy: IProxy, private val propertyName: String) {
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): PropertySetter = onInterface(interfaceName.value)
    fun onInterface(interfaceName: String): PropertySetter = apply {
        interfaceName_ = interfaceName
    }

    inline fun <reified T : Any> toValue(value: T, dontExpectReply: Boolean = false) {
        toValue(Variant<T>(value), dontExpectReply = dontExpectReply)
    }

    fun toValue(variant: Variant, dontExpectReply: Boolean = false) {
        require(interfaceName_?.isNotEmpty() == true)

        val call = proxy.callMethod("Set")
            .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName, variant) }
        if (dontExpectReply) {
            call.dontExpectReply()
        } else {
            call.readResult<Unit>()
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AsyncPropertySetter(private val proxy: IProxy, private val propertyName: String) {

    private var value_: Pair<Typed<*>, Any>? = null
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertySetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncPropertySetter = apply {
        interfaceName_ = interfaceName
    }

    inline fun <reified T : Any> toValue(value: T) = toValue(typed<T>(), value)

    fun <T : Any> toValue(type: Typed<T>, value: T): AsyncPropertySetter = apply {
        value_ = type to value
    }

    fun uponReplyInvoke(callback: TypedMethodCall<*>): PendingAsyncCall {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("Set")
        return shared
            .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName) + (value_!!) }
            .uponReplyInvoke(callback)
    }

    suspend fun getResult() {
        require(interfaceName_?.isNotEmpty() == true)

        memScoped {
            return proxy.callMethodAsync("Set")
                .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName_!!, propertyName) + (value_!!) }
                .getResult()
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AllPropertiesGetter(val proxy: IProxy) {
    fun onInterface(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): Map<PropertyName, Variant> {
        memScoped {
            @Suppress("UNCHECKED_CAST")
            return proxy.callMethod("GetAll")
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName) }
                .readResult<Map<PropertyName, Variant>>()
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AsyncAllPropertiesGetter(private val proxy: IProxy) {
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncAllPropertiesGetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncAllPropertiesGetter = apply {
        interfaceName_ = interfaceName
    }

    fun uponReplyInvoke(callback: TypedMethodCall<*>): PendingAsyncCall {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("GetAll")
        return shared
            .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!) }
            .uponReplyInvoke(callback)
    }

    suspend fun getResult(): Map<PropertyName, Variant> {
        require(interfaceName_?.isNotEmpty() == true)
        memScoped {
            return proxy.callMethodAsync("GetAll")
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName_!!) }
                .getResult()
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}
