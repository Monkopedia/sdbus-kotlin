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
 * A handle to resource that may be manually released.
 *
 * While all resources/registrations will be managed automatically and cleaned
 * up once GC'd, that is out of control of the callers. Most registration methods
 * or similar return a [Resource]. Calling [release] will remove any registrations or
 * temporary effects caused by the method that returned the [Resource].
 *
 * Calling [release] multiple times will have no effect. Once [release] is called on
 * an object, that object should no longer be interacted with.
 */
interface Resource {
    /**
     * Releases this resource and any child resources it may have.
     */
    fun release()
}
