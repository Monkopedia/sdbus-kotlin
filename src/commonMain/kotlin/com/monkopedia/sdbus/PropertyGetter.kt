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

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Gets value of a property of the D-Bus object
 *
 * @param propertyName Name of the property
 * @return A helper object for convenient getting of property value
 *
 * This is a high-level, convenience way of reading D-Bus property values that abstracts
 * from the D-Bus message concept. sdbus::Variant is returned which shall then be converted
 * to the real property type (implicit conversion is supported).
 *
 * Example of use:
 * ```
 * val state = object.getProperty(InterfaceName("com.kistler.foo"), PropertyName("state"))
 * ```
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun <reified T : Any> Proxy.getProperty(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): T = callMethod<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Get")) {
    call(interfaceName, propertyName)
}.get<T>()

inline fun <reified T : Any> Proxy.setProperty(
    interfaceName: InterfaceName,
    propertyName: PropertyName,
    value: T,
    dontExpectReply: Boolean = false
) {
    callMethod<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Set")) {
        this.dontExpectReply = dontExpectReply
        call(interfaceName, propertyName, Variant(value))
    }
}

inline fun <R, reified T : Any> Proxy.prop(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): ReadWriteProperty<R, T> = object : ReadWriteProperty<R, T> {
    override fun getValue(thisRef: R, property: KProperty<*>): T =
        getProperty(interfaceName, propertyName)

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        setProperty(interfaceName, propertyName, value)
    }
}

val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
