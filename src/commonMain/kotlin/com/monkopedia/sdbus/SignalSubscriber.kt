/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus

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

/**
 * Subscribes to a D-Bus signal and exposes its decoded payloads as a cold [Flow].
 *
 * The signal handler is registered when the flow is collected and unregistered when collection
 * stops. The [builder] selects how the signal arguments are decoded into [T] via [SignalSubscriber.call].
 *
 * @param interfaceName Interface that declares the signal
 * @param signalName Name of the signal to subscribe to
 * @param builder Configures decoding of the signal payload
 * @return A flow that emits one element per received signal
 */
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

/**
 * Builder context for subscribing to a signal, exposed as the receiver of the lambda passed to
 * [Proxy.onSignal] and [Proxy.signalFlow]. Bind the handler that decodes the signal payload via the
 * inherited [call]/[acall] overloads.
 */
class SignalSubscriber : TypedMethodBuilderContext() {
    /** The handler bound via [call]/[acall], invoked for each received signal. */
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
