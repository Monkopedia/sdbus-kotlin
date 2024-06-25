@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.sdbusRequire
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.posix.errno
import sdbus.CLOCK_MONOTONIC
import sdbus.clock_gettime
import sdbus.sd_bus_error
import sdbus.sd_bus_error_set
import sdbus.timespec

internal inline fun invokeHandlerAndCatchErrors(
    retError: CPointer<sd_bus_error>?,
    callable: () -> Unit
): Boolean {
    try {
        callable()
    } catch (e: Error) {
        sd_bus_error_set(retError, e.name, e.message);
        return false
    } catch (t: Throwable) {
        sd_bus_error_set(retError, SDBUSCPP_ERROR_NAME, t.message ?: "Unknown error occurred");
        return false
    }

    return true
}

internal inline fun now(): Duration = memScoped{
    val ts = cValue<timespec>().getPointer(this)
    val r = clock_gettime(CLOCK_MONOTONIC, ts)
    sdbusRequire(r < 0, "clock_gettime failed: ", -errno);

    return ts[0].tv_nsec.nanoseconds + ts[0].tv_sec.seconds
}

const val SDBUSCPP_ERROR_NAME = "org.sdbuscpp.Error"
