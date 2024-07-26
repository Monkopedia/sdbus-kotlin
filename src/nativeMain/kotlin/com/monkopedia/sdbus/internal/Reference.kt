/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.Resource
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

internal class Reference<T>(val value: T, onLeaveScopes: (T) -> Unit) : Resource {
    private val resource = value to singleCall(onLeaveScopes)

    fun get(): T = value
    private var extraReferences: MutableList<Reference<*>>? = null

    private val cleaner = createCleaner(resource) { (value, onLeaveScopes) ->
        onLeaveScopes.invoke(value)
    }

    override fun release() {
        extraReferences?.forEach { it.release() }
        resource.second(resource.first)
    }

    internal fun <R> freeAfter(value: Reference<R>): Resource {
        (extraReferences ?: mutableListOf<Reference<*>>().also { extraReferences = it })
            .add(value)
        return this
    }
}

private fun <T> singleCall(callback: (T) -> Unit): (T) -> Unit {
    return object : (T) -> Unit {
        private var hasCalled = false
        override fun invoke(p1: T) {
            if (hasCalled) return
            hasCalled = true
            callback(p1)
        }
    }
}
