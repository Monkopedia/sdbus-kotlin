package com.monkopedia.sdbus

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cValue
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import sdbus.sd_bus_error
import sdbus.sd_bus_error_free
import sdbus.sd_bus_error_set_errno

actual fun createError(errNo: Int, customMsg: String): Error {
    memScoped {
        val sdbusError: CPointer<sd_bus_error> = cValue<sd_bus_error>().getPointer(this)

        sd_bus_error_set_errno(sdbusError, errNo)
        defer { sd_bus_error_free(sdbusError) }

        val name = sdbusError[0].name?.toKString() ?: "$errNo"
        val message = buildString {
            append(customMsg)
            append(" (")
            append(sdbusError[0].message?.toKString() ?: "")
            append(')')
        }

        return Error(name, message)
    }
}
