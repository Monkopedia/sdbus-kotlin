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

package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.SD_BUS_VTABLE_DEPRECATED
import sdbus.SD_BUS_VTABLE_PROPERTY_CONST
import sdbus.SD_BUS_VTABLE_PROPERTY_EMITS_CHANGE
import sdbus.SD_BUS_VTABLE_PROPERTY_EMITS_INVALIDATION
import sdbus.SD_BUS_VTABLE_UNPRIVILEGED

internal fun Flags.toSdBusInterfaceFlags(): ULong {
    var sdbusFlags: ULong = 0u

    if (test(DEPRECATED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
    }
    if (!test(PRIVILEGED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED
    }

    sdbusFlags = testEmitsFlags(sdbusFlags)

    return sdbusFlags
}

internal fun Flags.toSdBusMethodFlags(): ULong {
    var sdbusFlags: ULong = 0u

    if (test(DEPRECATED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
    }
    if (!test(PRIVILEGED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED
    }
    if (test(METHOD_NO_REPLY)) {
        sdbusFlags = sdbusFlags or 0u // SD_BUS_VTABLE_METHOD_NO_REPLY
    }

    return sdbusFlags
}

internal fun Flags.toSdBusSignalFlags(): ULong {
    var sdbusFlags: ULong = 0u

    if (test(DEPRECATED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
    }

    return sdbusFlags
}

internal fun Flags.toSdBusPropertyFlags(): ULong {
    var sdbusFlags: ULong = 0u

    if (test(DEPRECATED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
    }
    // if (!test(GeneralFlags.PRIVILEGED))
    //    sdbusFlags |= SD_BUS_VTABLE_UNPRIVILEGED

    sdbusFlags = testEmitsFlags(sdbusFlags)

    return sdbusFlags
}

private fun Flags.testEmitsFlags(sdbusFlags: ULong): ULong = sdbusFlags or when {
    test(
        Flags.PropertyUpdateBehaviorFlags.EMITS_CHANGE_SIGNAL
    ) -> SD_BUS_VTABLE_PROPERTY_EMITS_CHANGE
    test(
        Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
    ) -> SD_BUS_VTABLE_PROPERTY_EMITS_INVALIDATION
    test(Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE) -> SD_BUS_VTABLE_PROPERTY_CONST
    test(Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL) -> 0u
    else -> 0u
}

internal fun Flags.toSdBusWritablePropertyFlags(): ULong {
    var sdbusFlags = toSdBusPropertyFlags()

    if (!test(PRIVILEGED)) {
        sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED
    }

    return sdbusFlags
}
