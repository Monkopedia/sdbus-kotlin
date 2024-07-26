package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.AsyncReplyHandler
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Resource

internal data class AsyncCallInfo(
    val callback: AsyncReplyHandler,
    val proxy: Proxy,
    val floating: Boolean,
    var finished: Boolean = false
) {
    var methodCall: Resource? = null
}
