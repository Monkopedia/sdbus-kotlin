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
@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.AsyncReplyHandler
import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalHandler
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.sdbusRequire
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.ref.createCleaner
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CompletableDeferred
import platform.posix.EINVAL
import sdbus.sd_bus_error
import sdbus.sd_bus_message_get_error

internal class ProxyImpl(
    override val connection: InternalConnection,
    private val destination: ServiceName,
    override val objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean = false
) : com.monkopedia.sdbus.Proxy {
    private class Allocs {
        val floatingAsyncCallSlots = FloatingAsyncCallSlots()
        val floatingSignalSlots = mutableListOf<Resource>()

        fun release() {
            floatingAsyncCallSlots.clear()
            floatingSignalSlots.forEach { it.release() }
            floatingSignalSlots.clear()
        }
    }

    private val allocs = Allocs()
    private val cleaner = createCleaner(allocs) {
        it.release()
    }

    override fun release() {
        allocs.release()
    }

    init {
        checkServiceName(destination.value)
        checkObjectPath(objectPath.value)
        if (!dontRunEventLoopThread) {
            connection.enterEventLoopAsync()
        }
    }

    override fun createMethodCall(
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall = connection.createMethodCall(destination, objectPath, interfaceName, methodName)

    override fun callMethod(message: MethodCall): MethodReply = callMethod(message, 0u)

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply {
        sdbusRequire(!message.isValid, "Invalid method call message provided", EINVAL)

        return connection.callMethod(message, timeout)
    }

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): com.monkopedia.sdbus.PendingAsyncCall = callMethodAsync(message, asyncReplyCallback, 0u)

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): com.monkopedia.sdbus.PendingAsyncCall {
        sdbusRequire(
            !message.isValid,
            "Invalid async method call message provided",
            EINVAL
        )

        val asyncCallInfo = AsyncCallInfo(
            callback = asyncReplyCallback,
            proxy = this@ProxyImpl,
            floating = false
        ).also { asyncCallInfo ->
            asyncCallInfo.attachMethodCall(
                connection.callMethod(
                    message,
                    sdbus_async_reply_handler,
                    WeakReference(asyncCallInfo),
                    timeout
                )
            )

            allocs.floatingAsyncCallSlots.pushBack(asyncCallInfo)
        }

        return com.monkopedia.sdbus.PendingAsyncCall(WeakReference(asyncCallInfo))
    }

    override suspend fun callMethodAsync(message: MethodCall): MethodReply =
        callMethodAsync(message, 0u)

    override suspend fun callMethodAsync(message: MethodCall, timeout: ULong): MethodReply {
        val deferred = CompletableDeferred<MethodReply>()

        callMethodAsync(message, deferred.asAsyncReplyHandler, timeout)

        try {
            return deferred.await()
        } catch (e: Error) {
            throw Error(e.name, e.errorMessage)
        }
    }

    override fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource {
        checkInterfaceName(interfaceName.value)
        checkMemberName(signalName.value)

        val signalInfo = SignalInfo(signalHandler, this@ProxyImpl)

        return connection.registerSignalHandler(
            destination.value,
            objectPath.value,
            interfaceName.value,
            signalName.value,
            sdbus_signal_handler,
            signalInfo
        ).also { slot ->
            allocs.floatingSignalSlots.add(slot)
        }
    }

    override val currentlyProcessedMessage: Message
        get() = connection.currentlyProcessedMessage

    internal fun erase(asyncCallInfo: AsyncCallInfo) {
        allocs.floatingAsyncCallSlots.erase(asyncCallInfo)
    }

    class SignalInfo(val callback: SignalHandler, val proxyImpl: ProxyImpl)

    // Container keeping track of pending async calls
    internal class FloatingAsyncCallSlots {
        private val asyncSlots = atomic<List<AsyncCallInfo>>(emptyList())

        fun pushBack(asyncCallInfo: AsyncCallInfo) {
            if (asyncCallInfo.finished) return
            while (true) {
                val current = asyncSlots.value
                if (asyncCallInfo.finished) return
                val updated = current + asyncCallInfo
                if (asyncSlots.compareAndSet(current, updated)) {
                    return
                }
            }
        }

        fun erase(info: AsyncCallInfo) {
            if (!info.markFinishedOnce()) return
            info.releaseMethodCallOnce()
            while (true) {
                val current = asyncSlots.value
                val idx = current.indexOf(info)
                if (idx < 0) return
                val updated = current.toMutableList().also { it.removeAt(idx) }
                if (asyncSlots.compareAndSet(current, updated)) {
                    return
                }
            }
        }

        fun clear() {
            val current = asyncSlots.getAndSet(emptyList())
            current.forEach {
                it.markFinishedOnce()
                it.releaseMethodCallOnce()
            }
        }
    }

    companion object {
        val sdbus_async_reply_handler =
            staticCFunction {
                    sdbusMessage: CPointer<sd_bus_message>?,
                    userData: COpaquePointer?,
                    retError: CPointer<sd_bus_error>?
                ->
                @Suppress("UNCHECKED_CAST")
                val reference = userData?.asStableRef<Any>()?.get() as? WeakReference<AsyncCallInfo>
                assert(reference != null)
                val asyncCallInfo = reference?.get() ?: return@staticCFunction -1
                val proxy = asyncCallInfo.proxy

                val ok = invokeHandlerAndCatchErrors(retError) {

                    try {
                        memScoped {

                            val message = MethodReply(
                                sdbusMessage!!,
                                (proxy as ProxyImpl).connection.getSdBusInterface()
                            )

                            val error = sd_bus_message_get_error(sdbusMessage)
                            if (error == null) {
                                asyncCallInfo.callback(message, null)
                            } else {
                                val exception = Error(
                                    error[0].name?.toKString() ?: "",
                                    error[0].message?.toKString() ?: ""
                                )
                                asyncCallInfo.callback(message, exception)
                            }
                        }
                        // We are removing the CallData item at the complete scope exit, after the callback has been invoked.
                        // We can't do it earlier (before callback invocation for example), because CallBack data (slot release)
                        // is the synchronization point between callback invocation and Proxy::unregister.
                    } finally {
                        (proxy as ProxyImpl).allocs.floatingAsyncCallSlots.erase(asyncCallInfo)
                    }
                }

                if (ok) 0 else -1
            }

        val sdbus_signal_handler =
            staticCFunction {
                    sdbusMessage: CPointer<sd_bus_message>?,
                    userData: COpaquePointer?,
                    retError: CPointer<sd_bus_error>?
                ->
                val signalInfo = userData?.asStableRef<Any>()?.get() as? SignalInfo
                assert(signalInfo != null)

                val ok = invokeHandlerAndCatchErrors(retError) {
                    // TODO: Hide Message factory invocation under Connection API (tell, don't ask principle), then we can remove getSdBusInterface()
                    val message = Signal(
                        sdbusMessage!!,
                        signalInfo!!.proxyImpl.connection.getSdBusInterface()
                    )
                    signalInfo.callback(message)
                }

                if (ok) 0 else -1
            }
    }
}

internal val CompletableDeferred<MethodReply>.asAsyncReplyHandler: AsyncReplyHandler
    get() = { reply, error ->
        if (error != null) {
            completeExceptionally(error)
        } else {
            complete(reply)
        }
    }
