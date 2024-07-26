package com.monkopedia.sdbus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.microseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

/**
 * Calls method on the D-Bus object asynchronously
 *
 * @param methodName Name of the method
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
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
suspend inline fun <reified R : Any> Proxy.callMethodAsync(
    interfaceName: InterfaceName,
    methodName: MethodName,
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

/**
 * Calls method on the D-Bus object
 *
 * @param methodName Name of the method
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
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun <reified R : Any> Proxy.callMethod(
    interfaceName: InterfaceName,
    methodName: MethodName,
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

class MethodInvoker @PublishedApi internal constructor(private val method: MethodCall) :
    TypedArgumentsBuilderContext() {

    var args: TypedArguments? = null
    var dontExpectReply by method::dontExpectReply

    var timeout: ULong = 0u
    var timeoutDuration: Duration
        get() = timeout.takeIf { it > 0u }?.toLong()?.microseconds ?: INFINITE
        set(value) {
            timeout = if (value == INFINITE) 0u else value.inWholeMicroseconds.toULong()
        }

    override fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        super.createCall(inputType, values).also {
            args = it
        }
}
