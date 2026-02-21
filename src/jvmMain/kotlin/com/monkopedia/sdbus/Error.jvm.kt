package com.monkopedia.sdbus

actual fun createError(errNo: Int, customMsg: String): Error = Error("jvm.errno.$errNo", customMsg)
