
package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_CHANGE_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL

// D-Bus interface, method, signal or property flags
class Flags {
    enum class GeneralFlags(val value: UByte) {
        DEPRECATED(0u),
        METHOD_NO_REPLY(1u),
        PRIVILEGED(2u)
    }

    enum class PropertyUpdateBehaviorFlags(val value: UByte) {
        EMITS_CHANGE_SIGNAL(3u),
        EMITS_INVALIDATION_SIGNAL(4u),
        EMITS_NO_SIGNAL(5u),
        CONST_PROPERTY_VALUE(6u)
    }

    enum class Count(val value: UByte) {
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
}

