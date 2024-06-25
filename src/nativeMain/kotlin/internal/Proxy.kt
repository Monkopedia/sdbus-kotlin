@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.IProxy
import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.MethodCall
import com.monkopedia.sdbus.header.MethodName
import com.monkopedia.sdbus.header.MethodReply
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PendingAsyncCall
import com.monkopedia.sdbus.header.sdbusRequire
import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.Signal
import com.monkopedia.sdbus.header.SignalName
import com.monkopedia.sdbus.header.async_reply_handler
import com.monkopedia.sdbus.header.dont_run_event_loop_thread
import com.monkopedia.sdbus.header.dont_run_event_loop_thread_t
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.header.return_slot_t
import com.monkopedia.sdbus.header.signal_handler
import com.monkopedia.sdbus.header.with_future_t
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.ref.createCleaner
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
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


internal class Proxy(
    private val connection_: IConnection,
    private val destination_: ServiceName,
    private val objectPath_: ObjectPath,
    @Suppress("UNUSED_PARAMETER") dont_run_event_loop_thread: dont_run_event_loop_thread_t
) : IProxy {
    private class Allocs {
        val floatingAsyncCallSlots_ = FloatingAsyncCallSlots()
        val floatingSignalSlots_ = mutableListOf<Slot>()

        fun release() {
            floatingAsyncCallSlots_.clear()
            floatingSignalSlots_.forEach { it.release() }
            floatingSignalSlots_.clear()
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
        checkServiceName(destination_.value)
        checkObjectPath(objectPath_.value)
    }


    constructor(
        connection: IConnection,
        destination: ServiceName,
        objectPath: ObjectPath
    ) : this(connection, destination, objectPath, dont_run_event_loop_thread) {
        connection.enterEventLoopAsync()
    }

    override fun createMethodCall(
        interfaceName: InterfaceName, methodName: MethodName
    ): MethodCall {
        return connection_.createMethodCall(destination_, objectPath_, interfaceName, methodName)
    }

    override fun createMethodCall(interfaceName: String, methodName: String): MethodCall {
        return connection_.createMethodCall(
            destination_.value, objectPath_.value, interfaceName, methodName
        )
    }

    override fun callMethod(message: MethodCall): MethodReply {
        return callMethod(message, 0u)
    }

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply {
        sdbusRequire(!message.isValid(), "Invalid method call message provided", EINVAL);

        return connection_.callMethod(message, timeout)
    }

    override fun callMethodAsync(
        message: MethodCall, asyncReplyCallback: async_reply_handler
    ): PendingAsyncCall {
        return callMethodAsync(message, asyncReplyCallback, 0u)
    }

    override fun callMethodAsync(
        message: MethodCall, asyncReplyCallback: async_reply_handler, timeout: ULong
    ): PendingAsyncCall {
        sdbusRequire(
            !message.isValid(), "Invalid async method call message provided", EINVAL
        );

        val asyncCallInfo = AsyncCallInfo(
            callback = asyncReplyCallback, proxy = this@Proxy, floating = false
        ).also { asyncCallInfo ->
            asyncCallInfo.methodCall = connection_.callMethod(
                message, sdbus_async_reply_handler, WeakReference(asyncCallInfo), timeout, return_slot
            )

            allocs.floatingAsyncCallSlots_.push_back(asyncCallInfo);
        }


        return PendingAsyncCall(WeakReference(asyncCallInfo))

    }

    override fun callMethodAsync(
        message: MethodCall, asyncReplyCallback: async_reply_handler, return_slot: return_slot_t
    ): Slot {
        return callMethodAsync(message, asyncReplyCallback, 0u, return_slot)
    }

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: async_reply_handler,
        timeout: ULong,
        return_slot: return_slot_t
    ): Slot {
        sdbusRequire(
            !message.isValid(), "Invalid async method call message provided", EINVAL
        )

        val asyncCallInfo =
            AsyncCallInfo(callback = asyncReplyCallback, proxy = this@Proxy, floating = true)

        asyncCallInfo.methodCall = connection_.callMethod(
            message, sdbus_async_reply_handler, WeakReference(asyncCallInfo), timeout, return_slot
        )

        return Reference(asyncCallInfo) {
            asyncCallInfo.methodCall = null
        }
    }

    override suspend fun callMethodAsync(
        message: MethodCall, with_future: with_future_t
    ): MethodReply {
        return callMethodAsync(message, 0u, with_future)
    }

    override suspend fun callMethodAsync(
        message: MethodCall, timeout: ULong, with_future: with_future_t
    ): MethodReply {
        val deferred = CompletableDeferred<MethodReply>()

        callMethodAsync(message, { reply, error ->
            if (error != null) {
                deferred.completeExceptionally(error)
            } else {
                deferred.complete(reply)
            }
        }, timeout);

        return deferred.await();
    }


    override fun registerSignalHandler(
        interfaceName: InterfaceName, signalName: SignalName, signalHandler: signal_handler
    ) {
        registerSignalHandler(interfaceName.value, signalName.value, signalHandler)
    }

    override fun registerSignalHandler(
        interfaceName: String, signalName: String, signalHandler: signal_handler
    ) {
        val slot = registerSignalHandler(interfaceName, signalName, signalHandler, return_slot)

        allocs.floatingSignalSlots_.add(slot)
    }

    override fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: signal_handler,
        return_slot: return_slot_t
    ): Slot {
        return registerSignalHandler(
            interfaceName.value, signalName.value, signalHandler, return_slot
        )
    }

    override fun registerSignalHandler(
        interfaceName: String,
        signalName: String,
        signalHandler: signal_handler,
        return_slot: return_slot_t
    ): Slot {
        checkInterfaceName(interfaceName);
        checkMemberName(signalName);

        val signalInfo = SignalInfo(signalHandler, this@Proxy)

        return connection_.registerSignalHandler(
            destination_.value,
            objectPath_.value,
            interfaceName,
            signalName,
            sdbus_signal_handler,
            signalInfo,
            return_slot
        )
    }

    override fun getConnection(): com.monkopedia.sdbus.header.IConnection {
        return connection_
    }

    override fun getObjectPath(): ObjectPath {
        return objectPath_
    }

    override fun getCurrentlyProcessedMessage(): Message {
        return connection_.getCurrentlyProcessedMessage()
    }

    internal fun erase(asyncCallInfo: Proxy.AsyncCallInfo) {
        allocs.floatingAsyncCallSlots_.erase(asyncCallInfo)
    }

    //    private:
//    static int sdbus_signal_handler(sd_bus_message *sdbusMessage, void *userData, sd_bus_error *retError);
//    static int sdbus_async_reply_handler(sd_bus_message *sdbusMessage, void *userData, sd_bus_error *retError);
//
//    private:
//    friend PendingAsyncCall;
//
//    std::unique_ptr< sdbus::internal::IConnection
//    , std::function<void(sdbus::internal::IConnection*)>
//    > connection_;
//    ServiceName destination_;
//    ObjectPath objectPath_;
//
//    std::vector<Slot> floatingSignalSlots_;
//
    class SignalInfo(
        val callback: signal_handler,
        val proxy: Proxy,
    ) {
        var signalHandler: Any? = null
    }

    data class AsyncCallInfo(
        val callback: async_reply_handler,
        val proxy: Proxy,
        val floating: Boolean,
        var finished: Boolean = false
    ) {
        var methodCall: Slot? = null
    }

    // Container keeping track of pending async calls
    class FloatingAsyncCallSlots {
        private val lock = ReentrantLock()
        private val asyncSlots = mutableListOf<AsyncCallInfo>()

        fun push_back(asyncCallInfo: AsyncCallInfo) {
            lock.withLock {
                asyncSlots.add(asyncCallInfo)
            }
        }

        fun erase(info: AsyncCallInfo) {
            lock.withLock {
                info.methodCall?.release()
                info.methodCall = null
                info.finished = true
                asyncSlots.remove(info)
            }
        }

        fun clear() {
            lock.withLock {
                asyncSlots.forEach { it.methodCall?.release() }
                asyncSlots.clear()
            }
        }
    }

    companion object {
        val sdbus_async_reply_handler =
            staticCFunction { sdbusMessage: CPointer<sd_bus_message>?, userData: COpaquePointer?, retError: CPointer<sd_bus_error>? ->
                val reference = userData?.asStableRef<Any>()?.get() as? WeakReference<AsyncCallInfo>
                assert(reference != null);
                val asyncCallInfo = reference?.get() ?: return@staticCFunction -1
                val proxy = asyncCallInfo.proxy;

                val ok = invokeHandlerAndCatchErrors(retError) {

                    try {
                        memScoped {

                            val message = MethodReply(
                                sdbusMessage!!, proxy.connection_.getSdBusInterface()
                            )

                            val error = sd_bus_message_get_error(sdbusMessage);
                            if (error == null) {
                                asyncCallInfo.callback(message, null);
                            } else {
                                val exception = Error(
                                    error.get(0).name?.toKString() ?: "",
                                    error[0].message?.toKString() ?: ""
                                );
                                asyncCallInfo.callback(message, exception);
                            }
                        }
                        // We are removing the CallData item at the complete scope exit, after the callback has been invoked.
                        // We can't do it earlier (before callback invocation for example), because CallBack data (slot release)
                        // is the synchronization point between callback invocation and Proxy::unregister.
                    } finally {
                        proxy.allocs.floatingAsyncCallSlots_.erase(asyncCallInfo);
                    };
                }

                if (ok) 0 else -1;
            }

        val sdbus_signal_handler =
            staticCFunction { sdbusMessage: CPointer<sd_bus_message>?, userData: COpaquePointer?, retError: CPointer<sd_bus_error>? ->
                val signalInfo = userData?.asStableRef<Any>()?.get() as? SignalInfo
                assert(signalInfo != null);

                val ok = invokeHandlerAndCatchErrors(retError) {
                    // TODO: Hide Message factory invocation under Connection API (tell, don't ask principle), then we can remove getSdBusInterface()
                    val message = Signal(
                        sdbusMessage!!, signalInfo!!.proxy.connection_.getSdBusInterface()
                    );
                    signalInfo!!.callback(message);
                }

                if (ok) 0 else -1;
            }

    }
}
