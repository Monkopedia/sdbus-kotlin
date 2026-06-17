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

package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_CHANGE_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL

/**
 * A mutable set of D-Bus interface, method, signal, and property flags.
 *
 * Flags describe behavioral attributes such as deprecation, privilege requirements, and property
 * change-emission behavior. They are attached to vtable items when registering an [Object].
 */
class Flags {
    /** General behavioral flags applicable to interfaces, methods and signals. */
    enum class GeneralFlags(val value: UByte) {
        /** Marks the element as deprecated. */
        DEPRECATED(0u),

        /** Indicates a method does not send a reply. */
        METHOD_NO_REPLY(1u),

        /** Marks the element as privileged. */
        PRIVILEGED(2u)
    }

    /** Flags controlling how a property signals changes to its value. */
    enum class PropertyUpdateBehaviorFlags(val value: UByte) {
        /** Emit a PropertiesChanged signal carrying the new value when the property changes. */
        EMITS_CHANGE_SIGNAL(3u),

        /** Emit a PropertiesChanged signal that only invalidates the property when it changes. */
        EMITS_INVALIDATION_SIGNAL(4u),

        /** Do not emit any signal when the property changes. */
        EMITS_NO_SIGNAL(5u),

        /** Marks the property value as constant; it never changes. */
        CONST_PROPERTY_VALUE(6u)
    }

    /** Internal marker carrying the total number of defined flags. */
    internal enum class Count(val value: UByte) {
        /** Sentinel value equal to the number of distinct flags. */
        FLAG_COUNT(7u)
    }

    private val flags = mutableSetOf(EMITS_CHANGE_SIGNAL.value)

    /**
     * Sets or clears a general flag.
     *
     * @param flag The flag to modify
     * @param value `true` to set the flag, `false` to clear it
     */
    fun set(flag: GeneralFlags, value: Boolean = true) {
        if (value) {
            flags.add(flag.value)
        } else {
            flags.remove(flag.value)
        }
    }

    /**
     * Selects the property update behavior, replacing any previously selected behavior.
     *
     * @param flag The property update behavior to apply
     * @param value `true` to set the behavior, `false` to clear it
     */
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

    /** Returns whether the given general [flag] is currently set. */
    fun test(flag: GeneralFlags): Boolean = flag.value in flags

    /** Returns whether the given property update behavior [flag] is currently selected. */
    fun test(flag: PropertyUpdateBehaviorFlags): Boolean = flag.value in flags
}
