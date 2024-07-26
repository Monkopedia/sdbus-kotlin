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
