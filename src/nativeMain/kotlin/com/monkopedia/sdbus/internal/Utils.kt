/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.sdbusRequire
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.errno
import platform.posix.timespec
import sdbus.sd_bus_error
import sdbus.sd_bus_error_set

internal inline fun invokeHandlerAndCatchErrors(
    retError: CPointer<sd_bus_error>?,
    callable: () -> Unit
): Boolean {
    try {
        callable()
    } catch (e: Error) {
        sd_bus_error_set(retError, e.name, e.message)
        return false
    } catch (t: Throwable) {
        sd_bus_error_set(retError, SDBUSCPP_ERROR_NAME, t.message ?: "Unknown error occurred")
        return false
    }

    return true
}

internal inline fun now(): Duration = memScoped {
    val ts = cValue<timespec>().getPointer(this)
    val r = clock_gettime(CLOCK_MONOTONIC, ts)
    sdbusRequire(r < 0, "clock_gettime failed: ", -errno)

    return ts[0].tv_nsec.nanoseconds + ts[0].tv_sec.seconds
}

internal const val SDBUSCPP_ERROR_NAME = "org.sdbuscpp.Error"
