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
 * Adds a declaration of methods, properties and signals of the object at a given interface
 *
 * @param interfaceName Name of the interface the vtable is registered under
 * @param builder Configures the vtable, declaring methods, properties, signals and flags
 * @return Resource handle to release registration
 *
 * This method is used to declare attributes for the object under the given interface.
 * The [builder] block defines a vtable that may contain method declarations (via [method]),
 * property declarations (via [prop]), signal declarations (via [signal]), or global interface
 * flags.
 *
 * An interface can have any number of vtables attached to it.
 *
 * Consult manual pages for the underlying `sd_bus_add_object_vtable` function for more information.
 *
 * The method can be called at any time during object's lifetime.
 *
 * Example 1:
 * ```
 * obj.addVTable(InterfaceName("com.monkopedia.foo")) {
 *   method(MethodName("Multiply")) {
 *     inputParamNames = listOf("a", "b")
 *     implementedAs(asyncCall(this@MyAdaptor::slowMultiply) withContext Dispatchers.IO)
 *   }
 *   method(MethodName("Divide")) {
 *     call(this@MyAdaptor::divide)
 *   }
 *   prop(PropertyName("Name")) {
 *     with(this@MyAdaptor::name)
 *   }
 * }
 * ```
 *
 * Example 2:
 * ```
 * obj.addVTable(InterfaceName("org.freedesktop.two.DBus.Properties")) {
 *   method(MethodName("Get")) {
 *     inputParamNames = listOf("interface_name", "property_name")
 *     outputParamNames = listOf("value")
 *     asyncCall(this@PropertiesAdaptor::`get`)
 *   }
 *   signal(SignalName("PropertiesChanged")) {
 *     with<String>("interface_name")
 *     with<Map<String, Variant>>("changed_properties")
 *     with<List<String>>("invalidated_properties")
 *   }
 * }
 * ```
 *
 * The function provides strong exception guarantee. The state of the object remains
 * unmodified in face of an exception.
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 * @see [com.monkopedia.sdbus.Resource]
 * @see [prop]
 * @see [method]
 * @see [signal]
 */
inline fun Object.addVTable(interfaceName: InterfaceName, builder: VTableBuilder.() -> Unit) =
    addVTable(interfaceName, buildList { VTableBuilder(this).builder() })

/**
 * Receiver for the [addVTable] DSL that accumulates vtable items.
 *
 * Items are added through the [method], [signal], [prop], and [interfaceFlags] builder functions
 * rather than by manipulating [items] directly.
 *
 * @property items The mutable list of vtable items being built
 */
@kotlin.jvm.JvmInline
value class VTableBuilder(val items: MutableList<VTableItem>)

/**
 * Common supertype for entries in an [Object]'s vtable: methods, signals, properties, and
 * interface-wide flags.
 */
sealed interface VTableItem
