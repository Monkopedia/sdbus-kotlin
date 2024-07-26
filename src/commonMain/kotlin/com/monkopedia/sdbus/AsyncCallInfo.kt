package com.monkopedia.sdbus

data class AsyncCallInfo(
    val callback: AsyncReplyHandler,
    val proxy: IProxy,
    val floating: Boolean,
    var finished: Boolean = false
) {
    var methodCall: Resource? = null
}
