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

/**
 * Builds a signal into the given vtable builder.
 *
 * @see addVTable
 */
inline fun VTableBuilder.signal(signal: SignalName, builder: SignalVTableItem.() -> Unit) {
    items.add(SignalVTableItem(signal).also(builder))
}

data class SignalVTableItem(
    val name: SignalName,
    var signature: Signature = Signature(""),
    var paramNames: List<String> = emptyList(),
    val flags: Flags = Flags()
) : VTableItem {

    /**
     * Adds a single parameter to the signal type.
     *
     * This is expected to be called once for each parameter of the signal.
     */
    inline fun <reified T> with(paramName: String) {
        signature += signatureOf<T>().value
        paramNames = paramNames + paramName
    }

    var isDeprecated: Boolean
        get() = flags.test(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }
}
