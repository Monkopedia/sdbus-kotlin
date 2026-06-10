package com.monkopedia.sdbus

internal actual fun createError(errNo: Int, customMsg: String): Error =
    Error("jvm.errno.$errNo", customMsg)
