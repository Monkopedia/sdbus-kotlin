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
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

/**
 * Creates a standalone [PropertyVTableItem] for the given property name.
 *
 * Library convention: `register*` functions return a [Resource] when the registration
 * must be explicitly released, and `Unit` (or the registered item, as here) otherwise.
 *
 * @param propertyName The property name
 * @return A new property vtable item
 */
fun registerProperty(propertyName: PropertyName): PropertyVTableItem =
    PropertyVTableItem(propertyName)

/**
 * Builds a property into the given vtable builder.
 *
 * @see addVTable
 */
inline fun VTableBuilder.prop(propertyName: PropertyName, builder: PropertyVTableItem.() -> Unit) {
    items.add(PropertyVTableItem(propertyName).also(builder))
}

/**
 * A vtable entry describing a property exported by an [Object].
 *
 * Construct one inside an [addVTable] block via [prop], then bind a getter and (optionally) a
 * setter with [withGetter]/[withSetter], or bind directly to a Kotlin property via [with].
 *
 * @property name The property name
 * @property signature D-Bus signature of the property value, derived when a getter/setter is bound
 * @property getter The callback that produces the property value, or `null` until bound
 * @property setter The callback that accepts a new property value, or `null` if read-only
 * @property flags Behavioral flags for this property
 */
class PropertyVTableItem @PublishedApi internal constructor(
    val name: PropertyName,
    var signature: Signature? = null,
    var getter: PropertyGetCallback? = null,
    var setter: PropertySetCallback? = null,
    val flags: Flags = Flags()
) : VTableItem {
    /**
     * Binds a read-only getter for this property, deriving the signature from type [T].
     *
     * @param callback Produces the current property value
     * @return This item, for chaining
     */
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

    /**
     * Binds a setter for this property, deriving the signature from type [T] if not already set.
     *
     * @param callback Receives the new property value supplied by a remote caller
     * @return This item, for chaining
     */
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

    /** Whether this property is marked deprecated. */
    var isDeprecated: Boolean
        get() = flags.has(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }

    /** Whether this property is marked as requiring privileged access. */
    var isPrivileged: Boolean
        get() = flags.has(PRIVILEGED)
        set(value) {
            flags.set(PRIVILEGED, value)
        }

    /** Applies the given property update [behavior] to this property's flags. */
    operator fun plusAssign(behavior: PropertyUpdateBehaviorFlags) {
        flags.set(behavior)
    }

    /** Applies this property update behavior to the property's flags via the `+` prefix operator. */
    operator fun PropertyUpdateBehaviorFlags.unaryPlus() {
        flags.set(this)
    }

    /**
     * Binds this property to a Kotlin property [receiver].
     *
     * A getter is always installed. If [receiver] is a [KMutableProperty0], a setter is installed
     * too, making the D-Bus property writable.
     *
     * @param receiver The backing Kotlin property
     */
    inline fun <reified T : Any> with(receiver: KProperty0<T>) {
        signature = Signature(signatureOf<T>().value)
        getter = { reply ->
            // Serialize the property value into the reply, mirroring withGetter; returning the
            // value from the lambda is not enough — the callback type is (PropertyGetReply) -> Unit.
            reply.serialize<T>(receiver.get())
        }
        if (receiver is KMutableProperty0) {
            setter = {
                receiver.set(it.deserialize<T>())
            }
        }
    }
}
