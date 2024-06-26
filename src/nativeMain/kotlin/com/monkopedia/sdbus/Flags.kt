@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_CHANGE_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.SD_BUS_VTABLE_DEPRECATED
import sdbus.SD_BUS_VTABLE_PROPERTY_CONST
import sdbus.SD_BUS_VTABLE_PROPERTY_EMITS_CHANGE
import sdbus.SD_BUS_VTABLE_PROPERTY_EMITS_INVALIDATION
import sdbus.SD_BUS_VTABLE_UNPRIVILEGED
import sdbus.uint64_t
import sdbus.uint8_t

// D-Bus interface, method, signal or property flags
class Flags {
    enum class GeneralFlags(val value: uint8_t) {
        DEPRECATED(0u),
        METHOD_NO_REPLY(1u),
        PRIVILEGED(2u)
    }

    enum class PropertyUpdateBehaviorFlags(val value: uint8_t) {
        EMITS_CHANGE_SIGNAL(3u),
        EMITS_INVALIDATION_SIGNAL(4u),
        EMITS_NO_SIGNAL(5u),
        CONST_PROPERTY_VALUE(6u)
    }

    enum class Count(val value: uint8_t) {
        FLAG_COUNT(7u)
    }

    private val flags = mutableSetOf(EMITS_CHANGE_SIGNAL.value)

    fun set(flag: GeneralFlags, value: Boolean = true) {
        if (value) {
            flags.add(flag.value)
        } else {
            flags.remove(flag.value)
        }
    }

    fun set(flag: PropertyUpdateBehaviorFlags, value: Boolean = true) {
        flags.remove(EMITS_CHANGE_SIGNAL.value)
        flags.remove(EMITS_INVALIDATION_SIGNAL.value)
        flags.remove(EMITS_NO_SIGNAL.value)
        flags.remove(CONST_PROPERTY_VALUE.value)

        if (value) {
            flags.add(flag.value)
        } else {
            flags.remove(flag.value)
        }
    }

    fun test(flag: GeneralFlags): Boolean = flag.value in flags

    fun test(flag: PropertyUpdateBehaviorFlags): Boolean = flag.value in flags

    internal fun toSdBusInterfaceFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED)) {
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
        }
        if (!test(PRIVILEGED)) {
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED
        }

        sdbusFlags = testEmitsFlags(sdbusFlags)

        return sdbusFlags
    }

    internal fun toSdBusMethodFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

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

    internal fun toSdBusSignalFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED)) {
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
        }

        return sdbusFlags
    }

    internal fun toSdBusPropertyFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED)) {
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
        }
        // if (!test(GeneralFlags.PRIVILEGED))
        //    sdbusFlags |= SD_BUS_VTABLE_UNPRIVILEGED

        sdbusFlags = testEmitsFlags(sdbusFlags)

        return sdbusFlags
    }

    private fun testEmitsFlags(sdbusFlags: uint64_t): uint64_t {
        return sdbusFlags or when {
            test(EMITS_CHANGE_SIGNAL) -> SD_BUS_VTABLE_PROPERTY_EMITS_CHANGE
            test(EMITS_INVALIDATION_SIGNAL) -> SD_BUS_VTABLE_PROPERTY_EMITS_INVALIDATION
            test(CONST_PROPERTY_VALUE) -> SD_BUS_VTABLE_PROPERTY_CONST
            test(EMITS_NO_SIGNAL) -> 0u
            else -> 0u
        }
    }

    internal fun toSdBusWritablePropertyFlags(): uint64_t {
        var sdbusFlags = toSdBusPropertyFlags()

        if (!test(PRIVILEGED)) {
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED
        }

        return sdbusFlags
    }
}
