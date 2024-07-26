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
package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

fun registerProperty(propertyName: PropertyName): PropertyVTableItem =
    PropertyVTableItem(propertyName)

inline fun VTableBuilder.prop(propertyName: PropertyName, builder: PropertyVTableItem.() -> Unit) {
    items.add(PropertyVTableItem(propertyName).also(builder))
}

data class PropertyVTableItem(
    val name: PropertyName,
    var signature: Signature? = null,
    var getter: PropertyGetCallback? = null,
    var setter: PropertySetCallback? = null,
    val flags: Flags = Flags()
) : VTableItem {
    inline fun <reified T : Any> withGetter(crossinline callback: () -> T): PropertyVTableItem =
        apply {
            if (signature == null) {
                signature = Signature(signatureOf<T>().value)
            }

            getter = { reply ->
                // Get the propety value and serialize it into the pre-constructed reply message
                reply.serialize<T>(callback())
            }
        }

    inline fun <reified T : Any> withSetter(crossinline callback: (T) -> Unit): PropertyVTableItem =
        apply {
            if (signature == null) {
                signature = Signature(signatureOf<T>().value)
            }
            setter = { call ->
                // Default-construct property value
                // Deserialize property value from the incoming call message
                val property = call.deserialize<T>()

                // Invoke setter with the value
                callback(property)
            }
        }

    var isDeprecated: Boolean
        get() = flags.test(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }
    var isPrivileged: Boolean
        get() = flags.test(PRIVILEGED)
        set(value) {
            flags.set(PRIVILEGED, value)
        }

    operator fun plusAssign(behavior: PropertyUpdateBehaviorFlags) {
        flags.set(behavior)
    }

    operator fun PropertyUpdateBehaviorFlags.unaryPlus() {
        flags.set(this)
    }

    inline fun <reified T : Any> with(receiver: KProperty0<T>) {
        signature = Signature(signatureOf<T>().value)
        getter = {
            receiver.get()
        }
        if (receiver is KMutableProperty0) {
            setter = {
                receiver.set(it.deserialize<T>())
            }
        }
    }
}
