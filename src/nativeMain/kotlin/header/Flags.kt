@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import com.monkopedia.sdbus.header.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.header.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.header.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.EMITS_CHANGE_SIGNAL
import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
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
    private val flags_ = mutableSetOf(EMITS_CHANGE_SIGNAL.value)


    fun set(flag: GeneralFlags, value: Boolean = true) {
        if (value) {
            flags_.add(flag.value)
        } else {
            flags_.remove(flag.value)
        }
    }

    fun set(flag: PropertyUpdateBehaviorFlags, value: Boolean = true) {
        flags_.remove(EMITS_CHANGE_SIGNAL.value)
        flags_.remove(EMITS_INVALIDATION_SIGNAL.value)
        flags_.remove(EMITS_NO_SIGNAL.value)
        flags_.remove(CONST_PROPERTY_VALUE.value)

        if (value) {
            flags_.add(flag.value)
        } else {
            flags_.remove(flag.value)
        }
    }

    fun test(flag: GeneralFlags): Boolean {
        return flag.value in flags_
    }

    fun test(flag: PropertyUpdateBehaviorFlags): Boolean {
        return flag.value in flags_
    }

    fun toSdBusInterfaceFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
        if (!test(PRIVILEGED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED

        if (test(EMITS_CHANGE_SIGNAL))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_PROPERTY_EMITS_CHANGE
        else if (test(EMITS_INVALIDATION_SIGNAL))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_PROPERTY_EMITS_INVALIDATION
        else if (test(CONST_PROPERTY_VALUE))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_PROPERTY_CONST
        else if (test(EMITS_NO_SIGNAL))
            sdbusFlags = sdbusFlags or 0u

        return sdbusFlags

    }

    fun toSdBusMethodFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
        if (!test(PRIVILEGED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED
        if (test(METHOD_NO_REPLY))
            sdbusFlags = sdbusFlags or 0u//SD_BUS_VTABLE_METHOD_NO_REPLY

        return sdbusFlags

    }

    fun toSdBusSignalFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED

        return sdbusFlags

    }

    fun toSdBusPropertyFlags(): uint64_t {
        var sdbusFlags: uint64_t = 0u

        if (test(DEPRECATED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_DEPRECATED
        //if (!test(GeneralFlags.PRIVILEGED))
        //    sdbusFlags |= SD_BUS_VTABLE_UNPRIVILEGED

        if (test(EMITS_CHANGE_SIGNAL))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_PROPERTY_EMITS_CHANGE
        else if (test(EMITS_INVALIDATION_SIGNAL))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_PROPERTY_EMITS_INVALIDATION
        else if (test(CONST_PROPERTY_VALUE))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_PROPERTY_CONST
        else if (test(EMITS_NO_SIGNAL))
            sdbusFlags = sdbusFlags or 0u

        return sdbusFlags

    }

    fun toSdBusWritablePropertyFlags(): uint64_t {
        var sdbusFlags = toSdBusPropertyFlags()

        if (!test(PRIVILEGED))
            sdbusFlags = sdbusFlags or SD_BUS_VTABLE_UNPRIVILEGED

        return sdbusFlags
    }

}
