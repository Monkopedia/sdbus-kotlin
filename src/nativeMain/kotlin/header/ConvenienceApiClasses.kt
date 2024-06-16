@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import com.monkopedia.sdbus.internal.CustomDeferScope
import com.monkopedia.sdbus.internal.Scope
import com.monkopedia.sdbus.internal.Slot
import com.monkopedia.sdbus.internal.Unowned
import kotlin.math.sign
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

internal fun TypedArguments.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST") val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.serialize(
    types: List<KSerializer<*>>,
    args: List<Any>,
    module: SerializersModule
) {
    for ((s, a) in types.zip(args)) {
        @Suppress("UNCHECKED_CAST")
        serialize(s as KSerializer<Any>, module, a)
    }
}

internal fun Message.serialize(typedArgs: TypedArguments) {
    val types = typedArgs.inputType.map { it.type }
    val args = typedArgs.values
    val module = typedArgs.module()
    serialize(types, args, module)
}

internal fun TypedMethod.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST") val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.deserialize(
    types: List<KSerializer<*>>,
    module: SerializersModule
): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return types.map { deserialize(it as KSerializer<Any>, module) }
}

internal fun Message.deserialize(typedArgs: TypedMethod): List<Any> {
    val types = typedArgs.inputType.map { it.type }
    val module = typedArgs.module()
    return deserialize(types, module)
}

class VTableAdder(private val object_: IObject, private val vtable: List<VTableItem>) {
    fun forInterface(interfaceName: InterfaceName) {
        object_.addVTable(interfaceName, vtable)
    }

    fun forInterface(interfaceName: String) {
        forInterface(InterfaceName(interfaceName))
    }

    fun forInterface(interfaceName: InterfaceName, return_slot: return_slot_t): Unowned<Slot> {
        return object_.addVTable(interfaceName, vtable, return_slot)
    }

    fun forInterface(interfaceName: String, return_slot: return_slot_t): Unowned<Slot> {
        return forInterface(InterfaceName(interfaceName), return_slot)
    }
}

class SignalEmitter : Scope {
    private var signal_: Signal? = null
    private val object_: IObject
    private val signalName_: String

    constructor(initialScope: CustomDeferScope, obj: IObject, signalName: String) : super(
        initialScope
    ) {
        this.object_ = obj
        this.signalName_ = signalName
    }

    constructor(initialScope: DeferScope, obj: IObject, signalName: String) : super(initialScope) {
        this.object_ = obj
        this.signalName_ = signalName
    }

    constructor(initialScope: CustomDeferScope, obj: IObject, signalName: SignalName) : this(
        initialScope,
        obj,
        signalName.value
    )

    constructor(
        initialScope: DeferScope,
        obj: IObject,
        signalName: SignalName
    ) : this(initialScope, obj, signalName.value)

    override fun onScopeCleared() {
        object_.emitSignal(signal_ ?: return)
    }

    fun onInterface(interfaceName: InterfaceName): SignalEmitter {
        return onInterface(interfaceName.value)
    }

    fun onInterface(interfaceName: String): SignalEmitter = apply {
        signal_ = object_.createSignal(interfaceName, signalName_)
    }

    inline fun withArguments(builder: TypedArgumentsBuilder) =
        withArguments(build(builder))

    fun withArguments(typedArgs: TypedArguments) {
        require(signal_ != null)
        signal_!!.serialize(typedArgs)
    }
}

class MethodInvoker : Scope {
    private var timeout_: ULong = 0u
    private var method_: MethodCall? = null
    private val proxy: IProxy
    private val methodName: String
    private var hasCalled = false
    private var exception: Throwable? = null

    constructor(initialScope: CustomDeferScope, proxy: IProxy, methodName: String) : super(
        initialScope
    ) {
        this.proxy = proxy
        this.methodName = methodName
    }

    constructor(initialScope: DeferScope, proxy: IProxy, methodName: String) : super(initialScope) {
        this.proxy = proxy
        this.methodName = methodName
    }

    constructor(
        initialScope: DeferScope,
        proxy: IProxy,
        methodName: MethodName
    ) : this(initialScope, proxy, methodName.value)

    override fun onScopeCleared() {
        if (hasCalled || exception != null) {
            return
        }
        memScoped {
            try {
                require(method_?.isValid() == true)
                proxy.callMethod(method_!!, timeout_)
                hasCalled = true
            } catch (t: Throwable) {
                exception = t
                throw t
            }
        }
    }

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
        require(method_?.isValid() == true)
        method_!!.serialize(typedArgs)
    }

    inline fun <reified T : Any> readResult(): T {
        val serializer = serializer<T>()
        return readResult(serializer, serializersModuleOf(serializer)) as T
    }

    fun <T : Any> readResult(serializer: DeserializationStrategy<T>, module: SerializersModule): T {
        try {
            memScoped {
                require(method_?.isValid() == true)
                val reply = proxy.callMethod(method_!!, timeout_)
                hasCalled = true

                return reply.deserialize(serializer, module)
            }
        } catch (t: Throwable) {
            exception = t
            throw t
        }
    }

    fun dontExpectReply() {
        require(method_?.isValid() == true)

        method_!!.dontExpectReply()
    }
};

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
        require(method_?.isValid() == true)

        method_!!.serialize(typedArgs)
    }

    fun uponReplyInvoke(callback: TypedMethodCall): PendingAsyncCall {
        require(method_?.isValid() == true)

        return proxy.callMethodAsync(method_!!, makeAsyncReplyHandler(callback), timeout_)
    }

    inline fun uponReplyInvoke(crossinline callbackBuilder: TypedMethodBuilder) =
        uponReplyInvoke(build(callbackBuilder))

    fun uponReplyInvoke(callback: TypedMethodCall, return_slot: return_slot_t): Unowned<Slot> {
        require(method_?.isValid() == true)

        return proxy.callMethodAsync(
            method_!!,
            makeAsyncReplyHandler(callback),
            timeout_,
            return_slot
        )
    }

    inline fun uponReplyInvoke(
        crossinline callbackBuilder: TypedMethodBuilder,
        return_slot: return_slot_t
    ) = uponReplyInvoke(build(callbackBuilder), return_slot)

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

    fun makeAsyncReplyHandler(callback: TypedMethodCall): async_reply_handler {
        return { reply, error ->
            val asyncCall = callback.asAsyncMethod()
            memScoped {
                if (error != null) {
                    asyncCall.errorCall?.invoke(error)
                } else {
                    try {
                        val args = reply.deserialize(asyncCall.method)
                        asyncCall.handler.invoke(args)
                    } catch (t: Throwable) {
                        asyncCall.errorCall?.invoke(t)
                    }
                }
            }
        }
    }

}

class SignalSubscriber(
    private val proxy: IProxy,
    private val signalName: String
) {
    private var interfaceName_: String? = null

    constructor(proxy: IProxy, signalName: SignalName) : this(proxy, signalName.value)

    fun onInterface(interfaceName: InterfaceName): SignalSubscriber =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): SignalSubscriber = apply {
        interfaceName_ = interfaceName
    }

    inline fun call(builder: TypedMethodBuilder): Unit = call(build(builder))
    fun call(callback: TypedMethodCall) {
        require(interfaceName_ != null)

        proxy.registerSignalHandler(interfaceName_!!, signalName, makeSignalHandler(callback))
    }

    inline fun call(builder: TypedMethodBuilder, return_slot: return_slot_t): Unowned<Slot> =
        call(build(builder), return_slot)

    fun call(callback: TypedMethodCall, return_slot: return_slot_t): Unowned<Slot> {
        require(interfaceName_ != null)

        return proxy.registerSignalHandler(
            interfaceName_!!,
            signalName,
            makeSignalHandler(callback),
            return_slot
        )
    }

    fun makeSignalHandler(callback: TypedMethodCall): signal_handler {
        return { signal ->
            val asyncCall = callback.asAsyncMethod()
            memScoped {
                try {
                    val args = signal.deserialize(asyncCall.method)
                    asyncCall.handler.invoke(args)
                } catch (t: Throwable) {
                    asyncCall.errorCall?.invoke(t)
                }
            }
        }
    }
}

class PropertyGetter(private val proxy: IProxy, private val propertyName: String) {

    inline fun <reified T : Any> onInterface(interfaceName: InterfaceName): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return onInterface(interfaceName.value, serializer, module, signature_of<T>())
    }

    inline fun <reified T : Any> onInterface(interfaceName: String): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return onInterface(interfaceName, serializer, module, signature_of<T>())
    }

    fun <T : Any> onInterface(
        interfaceName: String,
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: signature_of
    ): T {
        memScoped {
            return proxy.callMethod("Get").own(this)
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments {
                    call(interfaceName, propertyName)
                }
                .readResult<Variant>()
                .get(serializer, module, signature)
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties";
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

    fun uponReplyInvoke(callback: TypedMethodCall): PendingAsyncCall {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("Get")
        return shared
            .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName) }
            .uponReplyInvoke(callback)
    }

    inline fun uponReplyInvoke(
        crossinline callbackBuilder: TypedMethodBuilder,
        return_slot: return_slot_t
    ) = uponReplyInvoke(build(callbackBuilder), return_slot)

    fun uponReplyInvoke(callback: TypedMethodCall, return_slot: return_slot_t): Unowned<Slot> {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("Get")
        return shared
            .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName) }
            .uponReplyInvoke(callback, return_slot)
    }

    suspend inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return get(serializer, module, signature_of<T>())
    }

    suspend fun <T : Any> get(
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: signature_of
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
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties";

    }
}


class PropertySetter(private val proxy: IProxy, private val propertyName: String) {
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): PropertySetter = onInterface(interfaceName.value)
    fun onInterface(interfaceName: String): PropertySetter = apply {
        interfaceName_ = interfaceName
    }

    inline fun <reified T: Any> toValue(value: T) = memScoped {
        toValue(Variant<T>(value))
    }

    inline fun <reified T: Any> toValue(value: T, dont_expect_reply: dont_expect_reply_t) = memScoped {
        toValue(Variant<T>(value), dont_expect_reply)
    }

    fun toValue(variant: Variant) {
        require(interfaceName_?.isNotEmpty() == true)

        memScoped {
            proxy.callMethod("Set").own(this)
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName_!!, propertyName, variant) }
        }
    }

    fun toValue(variant: Variant, dont_expect_reply: dont_expect_reply_t) {
        require(interfaceName_?.isNotEmpty() == true)

        memScoped {
            proxy.callMethod("Set").own(this)
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName_!!, propertyName, variant) }
                .dontExpectReply()
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties";

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

    inline fun <reified T: Any> toValue(value: T) =
        toValue(typed<T>(), value)

    fun <T: Any> toValue(type: Typed<T>, value: T): AsyncPropertySetter = apply {
        value_ = type to value
    }

    fun uponReplyInvoke(callback: TypedMethodCall): PendingAsyncCall {

        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("Set")
        return shared
            .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName) + (value_!!) }
            .uponReplyInvoke(callback)
    }

    fun uponReplyInvoke(callback: TypedMethodCall, return_slot: return_slot_t): Unowned<Slot> {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("Set")
        return shared
            .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!, propertyName) + (value_!!) }
            .uponReplyInvoke(callback, return_slot)
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
};

class AllPropertiesGetter(val proxy: IProxy) {
    fun onInterface(
        interfaceName: InterfaceName
    ): Map<PropertyName, Variant> =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): Map<PropertyName, Variant> {
        memScoped {
            @Suppress("UNCHECKED_CAST")
            return proxy.callMethod("GetAll").own(this)
                .onInterface(DBUS_PROPERTIES_INTERFACE_NAME)
                .withArguments { call(interfaceName) }
                .readResult<Map<PropertyName, Variant>>()
        }

    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties";
    }
}

class AsyncAllPropertiesGetter(private val proxy: IProxy) {
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncAllPropertiesGetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncAllPropertiesGetter = apply {
        interfaceName_ = interfaceName
    }

    fun uponReplyInvoke(callback: TypedMethodCall): PendingAsyncCall {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("GetAll")
        return shared
            .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!) }
            .uponReplyInvoke(callback)
    }

    fun uponReplyInvoke(callback: TypedMethodCall, return_slot: return_slot_t): Unowned<Slot> {
        require(interfaceName_?.isNotEmpty() == true)

        val shared = proxy.callMethodAsync("GetAll")
        return shared
            .onInterface(PropertySetter.DBUS_PROPERTIES_INTERFACE_NAME)
            .withArguments { call(interfaceName_!!) }
            .uponReplyInvoke(callback, return_slot)
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
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties";
    }
};

