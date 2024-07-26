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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

class AsyncPropertyGetter(private val proxy: Proxy, private val propertyName: PropertyName) {

    private var interfaceName: InterfaceName? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertyGetter = apply {
        this.interfaceName = interfaceName
    }

    suspend inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return get(serializer, module, signatureOf<T>())
    }

    suspend fun <T : Any> get(
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T {
        require(interfaceName?.value?.isNotEmpty() == true)
        return proxy.callMethodAsync<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Get")) {
            call(interfaceName!!, propertyName)
        }.get(serializer, module, signature)
    }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

class AsyncPropertySetter(private val proxy: Proxy, private val propertyName: PropertyName) {

    private var value: Pair<Typed<*>, Any>? = null
    private var interfaceName: InterfaceName? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertySetter = apply {
        this.interfaceName = interfaceName
    }

    inline fun <reified T : Any> toValue(value: T) = toValue(typed<T>(), value)

    @PublishedApi
    internal fun <T : Any> toValue(type: Typed<T>, value: T): AsyncPropertySetter = apply {
        this.value = type to value
    }

    suspend fun getResult() {
        require(interfaceName?.value?.isNotEmpty() == true)

        return proxy.callMethodAsync<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Set")) {
            args = call(interfaceName!!, propertyName) + (value!!)
        }
    }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

class AllPropertiesGetter(val proxy: Proxy) {
    fun onInterface(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.callMethod(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("GetAll")) {
            call(interfaceName)
        }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

class AsyncAllPropertiesGetter(private val proxy: Proxy) {
    private var interfaceName: InterfaceName? = null

    fun onInterface(interfaceName: InterfaceName): AsyncAllPropertiesGetter = apply {
        this.interfaceName = interfaceName
    }

    suspend fun getResult(): Map<PropertyName, Variant> {
        require(interfaceName?.value?.isNotEmpty() == true)
        return proxy.callMethodAsync(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("GetAll")) {
            call(interfaceName!!)
        }
    }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}
