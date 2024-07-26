package com.monkopedia.sdbus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.microseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

/*!
 * @brief Calls method on the D-Bus object asynchronously
 *
 * @param[in] methodName Name of the method
 * @return A helper object for convenient asynchronous invocation of the method
 *
 * This is a high-level, convenience way of calling D-Bus methods that abstracts
 * from the D-Bus message concept. Method arguments/return value are automatically (de)serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments
 * and return values.
 *
 * Example of use:
 * @code
 * int a = ..., b = ...;
 * MethodName multiply{"multiply"};
 * object_.callMethodAsync(multiply).onInterface(INTERFACE_NAME).withArguments(a, b).uponReplyInvoke([](int result)
 * {
 *     std::cout << "Got result of multiplying " << a << " and " << b << ": " << result << std::endl;
 * });
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
suspend inline fun <reified R : Any> IProxy.callMethodAsync(
    interfaceName: InterfaceName,
    methodName: MethodName,
    builder: MethodInvoker.() -> Unit
): R = callMethodAsync(interfaceName.value, methodName.value, builder)
suspend inline fun <reified R : Any> IProxy.callMethodAsync(
    interfaceName: InterfaceName,
    methodName: String,
    builder: MethodInvoker.() -> Unit
): R = callMethodAsync(interfaceName.value, methodName, builder)

/*!
 * @copydoc IProxy::callMethodAsync(const MethodName&)
 */
suspend inline fun <reified R : Any> IProxy.callMethodAsync(
    interfaceName: String,
    methodName: String,
    builder: MethodInvoker.() -> Unit
): R {
    val method = createMethodCall(interfaceName, methodName)
    val invoker = MethodInvoker(method).also(builder)
    invoker.args?.let { method.serialize(it) }

    if (invoker.dontExpectReply) {
        callMethod(method, invoker.timeout)
        return (Unit as R)
    } else {
        val completable = CompletableDeferred<R>()
        callMethodAsync(method, { reply, error ->
            if (error != null) {
                completable.completeExceptionally(error)
            } else {
                try {
                    completable.complete(reply.deserialize<R>())
                } catch (t: Throwable) {
                    completable.completeExceptionally(t)
                }
            }
        }, invoker.timeout)

        return completable.await()
    }
}

/*!
 * @brief Calls method on the D-Bus object
 *
 * @param[in] methodName Name of the method
 * @return A helper object for convenient invocation of the method
 *
 * This is a high-level, convenience way of calling D-Bus methods that abstracts
 * from the D-Bus message concept. Method arguments/return value are automatically (de)serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments
 * and return values.
 *
 * Example of use:
 * @code
 * int result, a = ..., b = ...;
 * MethodName multiply{"multiply"};
 * object_.callMethod(multiply).onInterface(INTERFACE_NAME).withArguments(a, b).storeResultsTo(result);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun <reified R : Any> IProxy.callMethod(
    interfaceName: InterfaceName,
    methodName: MethodName,
    builder: MethodInvoker.() -> Unit
): R = callMethod(interfaceName.value, methodName.value, builder)

inline fun <reified R : Any> IProxy.callMethod(
    interfaceName: InterfaceName,
    methodName: String,
    builder: MethodInvoker.() -> Unit
): R = callMethod(interfaceName.value, methodName, builder)

/*!
 * @copydoc IProxy::callMethod(const MethodName&)
 */
inline fun <reified R : Any> IProxy.callMethod(
    interfaceName: String,
    methodName: String,
    builder: MethodInvoker.() -> Unit
): R {
    val method = createMethodCall(interfaceName, methodName)
    val invoker = MethodInvoker(method).also(builder)
    invoker.args?.let { method.serialize(it) }

    if (invoker.dontExpectReply) {
        callMethod(method, invoker.timeout)
        return (Unit as R)
    } else {
        val serializer = serializer<R>()
        val module = serializersModuleOf(serializer)
        val reply = callMethod(method, invoker.timeout)

        return reply.deserialize(serializer, module)
    }
}

class MethodInvoker(private val method: MethodCall) : TypedArgumentsBuilderContext() {

    var args: TypedArguments? = null
    var dontExpectReply by method::dontExpectReply

    var timeout: ULong = 0u
    var timeoutDuration: Duration
        get() = timeout.takeIf { it > 0u }?.let { it.toLong().microseconds } ?: Duration.INFINITE
        set(value) {
            timeout = if (value == INFINITE) 0u else value.inWholeMicroseconds.toULong()
        }

    override fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        super.createCall(inputType, values).also {
            args = it
        }
}

// class AsyncMethodInvoker {
//    private var timeout_: ULong = 0u
//    private var method_: MethodCall? = null
//    private val proxy: IProxy
//    private val methodName: String
//
//    constructor(proxy: IProxy, methodName: String) {
//        this.proxy = proxy
//        this.methodName = methodName
//    }
//
//    constructor(proxy: IProxy, methodName: MethodName) : this(proxy, methodName.value)
//
//    fun onInterface(interfaceName: InterfaceName): AsyncMethodInvoker =
//        onInterface(interfaceName.value)
//
//    fun onInterface(interfaceName: String): AsyncMethodInvoker = apply {
//        method_ = proxy.createMethodCall(interfaceName, methodName)
//    }
//
//    fun withTimeout(usec: ULong): AsyncMethodInvoker = apply {
//        timeout_ = usec
//    }
//
//    inline fun withTimeout(timeout: Duration): AsyncMethodInvoker =
//        withTimeout(timeout.inWholeMicroseconds.toULong())
//
//    inline fun withArguments(builder: TypedArgumentsBuilder): AsyncMethodInvoker =
//        withArguments(buildArgs(builder))
//
//    fun withArguments(typedArgs: TypedArguments): AsyncMethodInvoker = apply {
//        require(method_?.isValid == true)
//
//        method_!!.serialize(typedArgs)
//    }
//
//    fun uponReplyInvoke(callback: TypedMethodCall<*>): PendingAsyncCall {
//        require(method_?.isValid == true)
//
//    }
//
//    inline fun uponReplyInvoke(crossinline callbackBuilder: TypedMethodBuilder): PendingAsyncCall =
//        uponReplyInvoke(buildCall(callbackBuilder))
//
//    suspend inline fun <reified T> getResult(): T {
//    }
//
//    fun makeAsyncReplyHandler(callback: TypedMethodCall<*>): AsyncReplyHandler =
// }
