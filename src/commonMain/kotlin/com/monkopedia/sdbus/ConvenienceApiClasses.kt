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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

/**
 * Builder-style helper for asynchronously reading a single D-Bus property.
 *
 * Obtain an instance via [Proxy.getPropertyAsync], chain [onInterface] to select the interface,
 * then await the value with [get].
 */
class AsyncPropertyGetter(private val proxy: Proxy, private val propertyName: PropertyName) {

    private var interfaceName: InterfaceName? = null

    /** Selects the interface that declares the property and returns this builder. */
    fun onInterface(interfaceName: InterfaceName): AsyncPropertyGetter = apply {
        this.interfaceName = interfaceName
    }

    /**
     * Suspends until the property value is retrieved and deserialized as type [T].
     *
     * @return The current property value
     */
    suspend inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return get(serializer, module, signatureOf<T>())
    }

    /**
     * Suspends until the property value is retrieved and deserialized using the given strategy.
     *
     * @param serializer Deserialization strategy for the property value
     * @param module Serializers module providing any contextual serializers
     * @param signature Expected D-Bus signature of the property value
     * @return The current property value
     */
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
        /** The standard `org.freedesktop.DBus.Properties` interface name. */
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

/**
 * Builder-style helper for asynchronously writing a single D-Bus property.
 *
 * Obtain an instance via [Proxy.setPropertyAsync], chain [onInterface] and [toValue], then issue
 * the write with [getResult].
 */
class AsyncPropertySetter(private val proxy: Proxy, private val propertyName: PropertyName) {

    private var value: Pair<Typed<*>, Any>? = null
    private var interfaceName: InterfaceName? = null

    /** Selects the interface that declares the property and returns this builder. */
    fun onInterface(interfaceName: InterfaceName): AsyncPropertySetter = apply {
        this.interfaceName = interfaceName
    }

    /** Sets the value to write, deducing its type from the reified type [T]. */
    inline fun <reified T : Any> toValue(value: T) = toValue(typed<T>(), value)

    @PublishedApi
    internal fun <T : Any> toValue(type: Typed<T>, value: T): AsyncPropertySetter = apply {
        this.value = type to value
    }

    /** Suspends until the property write has completed. */
    suspend fun getResult() {
        require(interfaceName?.value?.isNotEmpty() == true)

        return proxy.callMethodAsync<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Set")) {
            args = call(interfaceName!!, propertyName) + (value!!)
        }
    }

    companion object {
        /** The standard `org.freedesktop.DBus.Properties` interface name. */
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

/**
 * Builder-style helper for synchronously reading all properties of a D-Bus interface.
 *
 * Obtain an instance via [Proxy.getAllProperties] and read the values with [onInterface].
 */
class AllPropertiesGetter(val proxy: Proxy) {
    /**
     * Reads all properties declared on the given interface.
     *
     * @return A map of property name to its current value
     */
    fun onInterface(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.callMethod(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("GetAll")) {
            call(interfaceName)
        }

    companion object {
        /** The standard `org.freedesktop.DBus.Properties` interface name. */
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

/**
 * Builder-style helper for asynchronously reading all properties of a D-Bus interface.
 *
 * Obtain an instance via [Proxy.getAllPropertiesAsync], chain [onInterface], then await the values
 * with [getResult].
 */
class AsyncAllPropertiesGetter(private val proxy: Proxy) {
    private var interfaceName: InterfaceName? = null

    /** Selects the interface whose properties will be read and returns this builder. */
    fun onInterface(interfaceName: InterfaceName): AsyncAllPropertiesGetter = apply {
        this.interfaceName = interfaceName
    }

    /**
     * Suspends until all properties on the selected interface have been retrieved.
     *
     * @return A map of property name to its current value
     */
    suspend fun getResult(): Map<PropertyName, Variant> {
        require(interfaceName?.value?.isNotEmpty() == true)
        return proxy.callMethodAsync(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("GetAll")) {
            call(interfaceName!!)
        }
    }

    companion object {
        /** The standard `org.freedesktop.DBus.Properties` interface name. */
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}
