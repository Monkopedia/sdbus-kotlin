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
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
inline fun <reified T : Any> Proxy.getProperty(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): T = callMethod<Variant>(PropertiesProxy.INTERFACE_NAME, MethodName("Get")) {
    call(interfaceName, propertyName)
}.get<T>()

/**
 * Sets a property on the D-Bus object.
 *
 * The value's signature is deduced from the reified type [T].
 *
 * @param interfaceName Interface that declares the property
 * @param propertyName Name of the property to set
 * @param value New value
 * @param dontExpectReply When `true`, send the set without waiting for a reply
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
inline fun <reified T : Any> Proxy.setProperty(
    interfaceName: InterfaceName,
    propertyName: PropertyName,
    value: T,
    dontExpectReply: Boolean = false
) {
    callMethod<Unit>(PropertiesProxy.INTERFACE_NAME, MethodName("Set")) {
        this.dontExpectReply = dontExpectReply
        call(interfaceName, propertyName, Variant(value))
    }
}

/**
 * Creates a read-only Kotlin property delegate backed by a D-Bus property.
 *
 * @param interfaceName Interface that declares the property
 * @param propertyName Name of the property
 * @return A [PropertyDelegate] usable with the `by` keyword
 */
inline fun <R, reified T : Any> Proxy.propDelegate(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): PropertyDelegate<R, T> {
    val type = serializer<T>()
    val module = serializersModuleOf(type)
    return PropertyDelegate(this, interfaceName, propertyName, type, module, signatureOf<T>())
}

/**
 * Creates a read/write Kotlin property delegate backed by a D-Bus property.
 *
 * @param interfaceName Interface that declares the property
 * @param propertyName Name of the property
 * @return A [MutablePropertyDelegate] usable with the `by` keyword
 */
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

/**
 * A read-only Kotlin property delegate backed by a D-Bus property.
 *
 * Reading the delegated property issues a D-Bus `Get`. The delegate also offers convenience
 * accessors ([getOrNull], [await]) and reactive observation of the property ([changes], [values],
 * [changesOrNull], [valuesOrNull]). Obtain one via [Proxy.propDelegate].
 *
 * The observation methods come in two families:
 * - [changes]/[changesOrNull] emit change events only — nothing is emitted until the property
 *   changes after collection starts.
 * - [values]/[valuesOrNull] emit the current value first, then all subsequent changes.
 *
 * Each family has a throwing and a nullable variant: [get], [changes] and [values] surface a
 * missing/invalidated property as an exception or by dropping the event, while [getOrNull],
 * [changesOrNull] and [valuesOrNull] represent it as `null`.
 *
 * @property interfaceName Interface that declares the property
 * @property propertyName Name of the property
 */
open class PropertyDelegate<R, T : Any>(
    protected val proxy: Proxy,
    val interfaceName: InterfaceName,
    val propertyName: PropertyName,
    protected val type: KSerializer<T>,
    protected val module: SerializersModule,
    protected val signature: TypeSignature
) : ReadOnlyProperty<R, T> {
    override fun getValue(thisRef: R, property: KProperty<*>): T = get()

    /**
     * Gets the current value of the property.
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure, including when the property
     * doesn't currently exist (use [getOrNull] for a null-returning variant)
     */
    fun get(): T = proxy.callMethod<Variant>(PropertiesProxy.INTERFACE_NAME, MethodName("Get")) {
        call(interfaceName, propertyName)
    }.get(type, module, signature)

    /**
     * Gets the current value of the property, however if the property doesn't currently exist
     * (`org.freedesktop.DBus.Error.InvalidArgs`), returns null rather than throwing. Any other
     * [com.monkopedia.sdbus.SdbusException] is still thrown.
     */
    fun getOrNull(): T? = try {
        get()
    } catch (e: SdbusException) {
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
     * Produces a flow that observes the `PropertiesChanged` signal and emits the new value of
     * this property each time it changes.
     *
     * This is a change-event stream only: nothing is emitted until the property changes after
     * collection starts (use [values] to also receive the current value first), and signals
     * that merely invalidate the property (without carrying a new value) are dropped (use
     * [changesOrNull] to observe invalidations as `null`).
     */
    fun changes(): Flow<T> = proxy.signalFlow<PropertiesChange>(
        PropertiesProxy.INTERFACE_NAME,
        SignalName("PropertiesChanged")
    ) {
        call(::PropertiesChange)
    }.mapNotNull { change ->
        change.changedProperties[propertyName]?.takeIf { change.interfaceName == interfaceName }
            ?.get(type, module, signature)
    }

    /**
     * Like [changes] but also emits `null` whenever the property has been invalidated (i.e. it
     * appears in the `invalidatedProperties` of a `PropertiesChanged` signal), instead of
     * dropping the event.
     *
     * Like [changes], this emits change events only; use [valuesOrNull] to also receive the
     * current value first.
     */
    fun changesOrNull(): Flow<T?> = proxy.signalFlow<PropertiesChange>(
        PropertiesProxy.INTERFACE_NAME,
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
     * Produces a flow of all values of the property: the current value (from [get]) is emitted
     * first when collection starts, followed by every subsequent change (from [changes]).
     *
     * Because the initial emission uses [get], collecting this flow throws
     * [com.monkopedia.sdbus.SdbusException] if the property doesn't currently exist; use [valuesOrNull]
     * for a null-emitting variant. Use [changes] to observe change events only, without the
     * initial value.
     */
    fun values(): Flow<T> = changes().onStart { emit(get()) }

    /**
     * Like [values] but null-safe: the current value (from [getOrNull], `null` if the property
     * doesn't currently exist) is emitted first when collection starts, followed by every
     * subsequent change (from [changesOrNull], which emits `null` on invalidation).
     */
    fun valuesOrNull(): Flow<T?> = changesOrNull().onStart { emit(getOrNull()) }

    @Serializable
    private data class PropertiesChange(
        val interfaceName: InterfaceName,
        val changedProperties: Map<PropertyName, Variant>,
        val invalidatedProperties: List<PropertyName>
    )
}

/**
 * A read/write Kotlin property delegate backed by a D-Bus property.
 *
 * Extends [PropertyDelegate] with write support: assigning to the delegated property issues a
 * D-Bus `Set`. Obtain one via [Proxy.mutableDelegate].
 */
class MutablePropertyDelegate<R, T : Any>(
    proxy: Proxy,
    interfaceName: InterfaceName,
    propertyName: PropertyName,
    private val serializer: KSerializer<T>,
    module: SerializersModule,
    signature: TypeSignature
) : PropertyDelegate<R, T>(proxy, interfaceName, propertyName, serializer, module, signature),
    ReadWriteProperty<R, T> {

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) = set(value)

    /**
     * Sets a new value for a mutable property.
     */
    fun set(value: T) {
        proxy.callMethod<Unit>(PropertiesProxy.INTERFACE_NAME, MethodName("Set")) {
            call(interfaceName, propertyName, Variant(serializer, module, value))
        }
    }
}

/**
 * Creates a simple read/write Kotlin property delegate backed by a D-Bus property.
 *
 * Each read issues a `Get` and each write issues a `Set`. Unlike [mutableDelegate], the returned
 * delegate performs no caching and offers no reactive observation.
 *
 * @param interfaceName Interface that declares the property
 * @param propertyName Name of the property
 * @return A [ReadWriteProperty] delegate usable with the `by` keyword
 */
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
