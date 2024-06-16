@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import sdbus.sd_bus_error
import sdbus.sd_bus_error_free
import sdbus.sd_bus_error_set_errno

class Error(val name: String, val errorMessage: String) : Exception("$name: $errorMessage") {
    val isValid: Boolean
        get() {
            return name.isNotEmpty()
        }
}

fun Throwable.toError() = (this as? Error) ?: Error(message ?: toString(), stackTraceToString())

fun createError(errNo: Int, customMsg: String): Error {
    memScoped {
        val sdbusError: CPointer<sd_bus_error> = cValue<sd_bus_error>().getPointer(this)

        sd_bus_error_set_errno(sdbusError, errNo);
        defer { sd_bus_error_free(sdbusError) }

        val name = sdbusError[0].name?.toKString() ?: "$errNo"
        val message = buildString {
            append(customMsg)
            append(" (")
            append(sdbusError[0].message?.toKString() ?: "")
            append(')')
        }

        return Error(name, message);
    }

}


inline fun SDBUS_THROW_ERROR(_MSG: String, _ERRNO: Int) {
    throw createError((_ERRNO), (_MSG))
}

inline fun SDBUS_THROW_ERROR_IF(_COND: () -> Boolean, _MSG: String, _ERRNO: Int) {
    if (_COND()) SDBUS_THROW_ERROR((_MSG), (_ERRNO))
}

inline fun SDBUS_THROW_ERROR_IF(_COND: Boolean, _MSG: String, _ERRNO: Int) {
    if (_COND) SDBUS_THROW_ERROR((_MSG), (_ERRNO))
}
