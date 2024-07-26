package com.monkopedia.sdbus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.microseconds
import kotlinx.coroutines.CancellationException
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
 * ```
 * val a: Int = ...
 * val b: Int = ...
 * val multiply = MethodName("multiply")
 * val result = object.callMethodAsync(INTERFACE_NAME, multiply) {
 *   call(a, b)
 * }
 * println("Got result of multiplying $a and $b: $result")
 * ```
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
        val call = callMethodAsync(method, { reply, error ->
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

        try {
            return completable.await()
        } catch (t: CancellationException) {
            call.release()
            throw t
        }
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
 * ```
 * val a: Int = ...
 * val b: Int = ...
 * val multiply = MethodName("multiply")
 * val result = object.callMethod(INTERFACE_NAME, multiply) {
 *   call(a, b)
 * }
 * println("Got result of multiplying $a and $b: $result")
 * ```
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

    var timeout: Duration = INFINITE

    override fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        super.createCall(inputType, values).also {
            args = it
        }
}
