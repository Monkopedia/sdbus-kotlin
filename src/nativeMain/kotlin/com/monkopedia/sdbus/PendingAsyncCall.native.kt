@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.AsyncCallInfo
import com.monkopedia.sdbus.internal.ProxyImpl
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/********************************************/
/**
 * @class PendingAsyncCall
 *
 * PendingAsyncCall represents a simple handle type to cancel the delivery
 * of the asynchronous D-Bus call result to the application.
 *
 * The handle is lifetime-independent from the originating Proxy object.
 * It's safe to call its methods even after the Proxy has gone.
 *
 ***********************************************/
actual class PendingAsyncCall internal constructor(
    private val target: WeakReference<AsyncCallInfo>
) : Resource {

    /**
     * Cancels the delivery of the pending asynchronous call result
     *
     * This function effectively removes the callback handler registered to the
     * async D-Bus method call result delivery. Does nothing if the call was
     * completed already, or if the originating Proxy object has gone meanwhile.
     */
    actual fun cancel() {
        val asyncCallInfo = target.get() ?: return
        (asyncCallInfo.proxy as ProxyImpl).erase(asyncCallInfo)
    }

    actual override fun release() {
        val asyncCallInfo = target.get() ?: return
        (asyncCallInfo.proxy as ProxyImpl).erase(asyncCallInfo)
    }

    /**
     * Answers whether the asynchronous call is still pending
     *
     * @return True if the call is pending, false if the call has been fully completed
     *
     * Pending call in this context means a call whose results have not arrived, or
     * have arrived and are currently being processed by the callback handler.
     */
    actual fun isPending(): Boolean = target.get()?.finished == false
}
