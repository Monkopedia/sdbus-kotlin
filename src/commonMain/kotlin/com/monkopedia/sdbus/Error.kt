@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get

class Error(val name: String, val errorMessage: String = "") : Exception("$name: $errorMessage")

internal fun Throwable.toError() =
    (this as? Error) ?: Error(message ?: toString(), stackTraceToString())

expect fun createError(errNo: Int, customMsg: String): Error

internal inline fun sdbusRequire(condition: () -> Boolean, msg: String, errNo: Int) {
    if (condition()) throw createError((errNo), (msg))
}

internal inline fun sdbusRequire(condition: Boolean, msg: String, errNo: Int) {
    if (condition) throw createError((errNo), (msg))
}
