package com.monkopedia.sdbus

import com.monkopedia.sdbus.TypedMethodCall.AsyncMethodCall
import com.monkopedia.sdbus.TypedMethodCall.SyncMethodCall
import kotlin.coroutines.CoroutineContext

/*!
 * @brief Registers signal handler for a given signal of the D-Bus object
 *
 * @param[in] signalName Name of the signal
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
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.onSignal(
    interfaceName: InterfaceName,
    signalName: SignalName,
    builder: SignalSubscriber.() -> Unit
): Resource {
    return onSignal(interfaceName.value, signalName.value, builder)
}

inline fun IProxy.onSignal(
    interfaceName: String,
    signalName: String,
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

class SignalSubscriber : TypedMethodBuilderContext() {
    var methodCall: TypedMethodCall<*>? = null

    override fun createCall(
        method: TypedMethod,
        handler: (List<Any?>) -> Any?,
        errorCall: ((Throwable?) -> Unit)?
    ): SyncMethodCall = super.createCall(method, handler, errorCall).also { methodCall = it }

    override fun createACall(
        method: TypedMethod,
        handler: suspend (List<Any?>) -> Any?,
        errorCall: (suspend (Throwable?) -> Unit)?,
        coroutineContext: CoroutineContext
    ): AsyncMethodCall = super.createACall(method, handler, errorCall, coroutineContext)
        .also { methodCall = it }
}
