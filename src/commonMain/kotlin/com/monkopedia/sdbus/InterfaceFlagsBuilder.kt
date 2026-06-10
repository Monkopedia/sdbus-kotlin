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

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags

/**
 * Adds interface-wide flags to the vtable in progress.
 *
 * @see addVTable
 */
inline fun VTableBuilder.interfaceFlags(builder: InterfaceFlagsVTableItem.() -> Unit) {
    items.add(InterfaceFlagsVTableItem().also(builder))
}

/**
 * A vtable entry carrying flags that apply to the whole interface rather than a single member.
 *
 * Construct one inside an [addVTable] block via [interfaceFlags].
 *
 * @property flags The underlying flag set
 */
class InterfaceFlagsVTableItem @PublishedApi internal constructor(val flags: Flags = Flags()) :
    VTableItem {

    /** Whether the interface is marked deprecated. */
    var isDeprecated: Boolean
        get() = flags.test(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }

    /** Whether the interface is marked as requiring privileged access. */
    var isPrivileged: Boolean
        get() = flags.test(PRIVILEGED)
        set(value) {
            flags.set(PRIVILEGED, value)
        }

    /** Whether methods on the interface default to not producing a reply. */
    var hasNoReply: Boolean
        get() = flags.test(METHOD_NO_REPLY)
        set(value) {
            flags.set(METHOD_NO_REPLY, value)
        }

    /** Applies the given property update [behavior] to the interface flags. */
    operator fun plusAssign(behavior: PropertyUpdateBehaviorFlags) {
        flags.set(behavior)
    }

    /** Applies this property update behavior to the interface flags via the `+` prefix operator. */
    operator fun PropertyUpdateBehaviorFlags.unaryPlus() {
        flags.set(this)
    }
}
