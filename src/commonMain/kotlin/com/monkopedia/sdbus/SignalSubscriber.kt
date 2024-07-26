package com.monkopedia.sdbus

import com.monkopedia.sdbus.TypedMethodCall.AsyncMethodCall
import com.monkopedia.sdbus.TypedMethodCall.SyncMethodCall
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Registers signal handler for a given signal of the D-Bus object
 *
 * @param signalName Name of the signal
 * @return A helper object for convenient registration of the signal handler
 *
 * This is a high-level, convenience way of registering to D-Bus signals that abstracts
 * from the D-Bus message concept. Signal arguments are automatically serialized
 * in a message and D-Bus signatures automatically deduced from the parameters
 * of the provided native signal callback.
 *
 * A signal can be subscribed to and unsubscribed from at any time during proxy
 * lifetime. The subscription is active immediately after the call.
 *
 * Example of use:
 * @code
 * object_.uponSignal("stateChanged").onInterface("com.kistler.foo").call([this](int arg1, double arg2){ this->onStateChanged(arg1, arg2); });
 * sdbus::InterfaceName foo{"com.kistler.foo"};
 * sdbus::SignalName levelChanged{"levelChanged"};
 * object_.uponSignal(levelChanged).onInterface(foo).call([this](uint16_t level){ this->onLevelChanged(level); });
 * @endcode
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun Proxy.onSignal(
    interfaceName: InterfaceName,
    signalName: SignalName,
    builder: SignalSubscriber.() -> Unit
): Resource {
    val methodCall = SignalSubscriber().also(builder).methodCall
        ?: error("No method call specified for signal handler")
    return registerSignalHandler(
        interfaceName,
        signalName
    ) { signal ->
        methodCall.invoke(signal)
    }
}

inline fun <T> Proxy.signalFlow(
    interfaceName: InterfaceName,
    signalName: SignalName,
    builder: SignalSubscriber.() -> Unit
): Flow<T> {
    val methodCall = SignalSubscriber().also(builder).methodCall
        ?: error("No method call specified for signal handler")
    return callbackFlow {
        val registration = registerSignalHandler(
            interfaceName,
            signalName
        ) { signal ->
            methodCall.invoke(signal, onSuccess = { _, res ->
                @Suppress("UNCHECKED_CAST")
                channel.trySendBlocking(res as T)
            }, onFailure = {
                channel.close(it)
            })
        }
        awaitClose {
            registration.release()
        }
    }
}

class SignalSubscriber : TypedMethodBuilderContext() {
    var methodCall: TypedMethodCall<*>? = null

    override fun createCall(
        method: TypedMethod,
        handler: (List<Any?>) -> Any?,
        errorCall: ((Throwable?) -> Unit)?
    ): TypedMethodCall<*> = super.createCall(method, handler, errorCall).also { methodCall = it }

    override fun createACall(
        method: TypedMethod,
        handler: suspend (List<Any?>) -> Any?,
        errorCall: (suspend (Throwable?) -> Unit)?,
        coroutineContext: CoroutineContext
    ): TypedMethodCall<*> = super.createACall(method, handler, errorCall, coroutineContext)
        .also { methodCall = it }
}
