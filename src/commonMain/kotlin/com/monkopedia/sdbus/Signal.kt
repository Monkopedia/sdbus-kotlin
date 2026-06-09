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

/**
 * A [Message] representing a D-Bus signal.
 *
 * Created via [Object.createSignal]. Serialize the signal arguments into it, optionally restrict
 * delivery with [setDestination], then emit it with [Object.emitSignal] or [send].
 */
expect class Signal : Message {

    /**
     * Restricts this signal to a single destination bus name instead of broadcasting it.
     *
     * @param destination Bus name of the intended recipient
     */
    fun setDestination(destination: String)

    /**
     * Emits this signal on the bus.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun send()
}
