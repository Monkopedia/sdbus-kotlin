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

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

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

inline fun <R, reified T : Any> Proxy.propDelegate(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): PropertyDelegate<R, T> {
    val type = serializer<T>()
    val module = serializersModuleOf(type)
    return PropertyDelegate(this, interfaceName, propertyName, type, module, signatureOf<T>())
}

inline fun <R, reified T : Any> Proxy.mutableDelegate(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): MutablePropertyDelegate<R, T> {
    val type = serializer<T>()
    val module = serializersModuleOf(type)
    return MutablePropertyDelegate(
        this,
        interfaceName,
        propertyName,
        type,
        module,
        signatureOf<T>()
    )
}

open class PropertyDelegate<R, T: Any>(
    protected val proxy: Proxy,
    val interfaceName: InterfaceName,
    val propertyName: PropertyName,
    protected val type: KSerializer<T>,
    protected val module: SerializersModule,
    protected val signature: SdbusSig
) : ReadOnlyProperty<R, T> {
    val name: String
        get() = propertyName.value

    override fun getValue(thisRef: R, property: KProperty<*>): T = get()

    /**
     * Get the current value of the property.
     */
    fun get(): T = proxy.callMethod<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Get")) {
        call(interfaceName, propertyName)
    }.get(type, module, signature)

    /**
     * Gets the current value of the property, however if the property doesn't currently exist,
     * returns null rather than throwing.
     */
    fun getOrNull(): T? = try {
        get()
    } catch (e: Error) {
        if (e.name != "org.freedesktop.DBus.Error.InvalidArgs") {
            throw e
        }
        null
    }

    /**
     * Waits for the property to be present and returns the first value when it is.
     * If the property is currently valid, then it is returned immediately.
     */
    suspend fun await(): T = getOrNull() ?: changesOrNull().filterNotNull().first()

    /**
     * Produces a flow that observes the properties changed signal of a [PropertiesProxy]
     * and will emit the new values for this property when it has changed.
     */
    fun changes(): Flow<T> = proxy.signalFlow<PropertiesChange>(
        DBUS_PROPERTIES_INTERFACE_NAME,
        SignalName("PropertiesChanged")
    ) {
        call(::PropertiesChange)
    }.mapNotNull { change ->
        change.changedProperties[propertyName]?.takeIf { change.interfaceName == interfaceName }
            ?.get(type, module, signature)
    }

    /**
     * Like [changes] but also will emit null whenever the property has been invalidated.
     */
    fun changesOrNull(): Flow<T?> = proxy.signalFlow<PropertiesChange>(
        DBUS_PROPERTIES_INTERFACE_NAME,
        SignalName("PropertiesChanged")
    ) {
        call(::PropertiesChange)
    }.filter {
        it.interfaceName == interfaceName &&
            (propertyName in it.changedProperties || propertyName in it.invalidatedProperties)
    }.map {
        it.changedProperties[propertyName]?.get(type, module, signature)
    }

    /**
     * Emits all the values from [changes] but also emits value from [get] at start.
     */
    fun flow(): Flow<T> = changes().onStart { emit(get()) }

    /**
     * Emits all the values from [changesOrNull] but also emits value from [getOrNull] at start.
     */
    fun flowOrNull(): Flow<T?> = changesOrNull().onStart { emit(getOrNull()) }

    @Serializable
    private data class PropertiesChange(
        val interfaceName: InterfaceName,
        val changedProperties: Map<PropertyName, Variant>,
        val invalidatedProperties: List<PropertyName>
    )
}

class MutablePropertyDelegate<R, T : Any>(
    proxy: Proxy,
    interfaceName: InterfaceName,
    propertyName: PropertyName,
    private val serializer: KSerializer<T>,
    module: SerializersModule,
    signature: SdbusSig
) : PropertyDelegate<R, T>(proxy, interfaceName, propertyName, serializer, module, signature),
    ReadWriteProperty<R, T> {

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) = set(value)

    /**
     * Sets a new value for a mutable property.
     */
    fun set(value: T) {
        proxy.callMethod<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Set")) {
            call(interfaceName, propertyName, Variant(serializer, module, value))
        }
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
