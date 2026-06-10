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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Mixin interface for types that wrap a [Proxy], used by the standard-interface proxy helpers.
 */
interface ProxyHolder {
    /** The underlying proxy used to issue calls and subscribe to signals. */
    val proxy: Proxy
}

/**
 * Wraps a [Proxy] in a [PeerProxy] exposing the standard `org.freedesktop.DBus.Peer` interface.
 *
 * @param proxy The proxy to wrap
 * @return A [PeerProxy] backed by [proxy]
 */
fun PeerProxy(proxy: Proxy): PeerProxy = object : PeerProxy {
    override val proxy: Proxy = proxy
}

/**
 * Client-side helper for the standard `org.freedesktop.DBus.Peer` interface.
 */
interface PeerProxy : ProxyHolder {
    /** Pings the remote peer to verify it is reachable. */
    fun ping(): Unit = proxy.callMethod(INTERFACE_NAME, SignalName("Ping")) {}

    /** Returns the machine UUID reported by the remote peer. */
    fun getMachineId(): String = proxy.callMethod(INTERFACE_NAME, SignalName("GetMachineId")) {}

    companion object {
        /** The standard `org.freedesktop.DBus.Peer` interface name. */
        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Peer")
    }
}

/**
 * Wraps a [Proxy] in a [PropertiesProxy] exposing the standard
 * `org.freedesktop.DBus.Properties` interface.
 *
 * @param proxy The proxy to wrap
 * @return A [PropertiesProxy] backed by [proxy]
 */
fun PropertiesProxy(proxy: Proxy): PropertiesProxy = object : PropertiesProxy {
    override val proxy: Proxy = proxy
}

/**
 * Client-side helper for the standard `org.freedesktop.DBus.Properties` interface.
 *
 * Provides reading and writing of properties, plus subscription to the PropertiesChanged signal.
 */
interface PropertiesProxy : ProxyHolder {

    /**
     * Subscribes to the PropertiesChanged signal so that [onPropertiesChanged] is invoked on
     * updates. Call this once after constructing the proxy.
     *
     * The subscription stays active for the lifetime of [proxy]. Library convention:
     * `register*` functions return a [Resource] when the registration must be explicitly
     * released, and `Unit` (or the registered item) otherwise.
     *
     * @param onPropertiesChanged Called when a PropertiesChanged signal is received, with the
     * interface whose properties changed, the map of changed property names to their new
     * values, and the names of properties that were invalidated without a new value
     */
    fun registerPropertiesProxy(
        onPropertiesChanged: (
            interfaceName: InterfaceName,
            changedProperties: Map<PropertyName, Variant>,
            invalidatedProperties: List<PropertyName>
        ) -> Unit
    ) {
        proxy.onSignal(INTERFACE_NAME, SignalName("PropertiesChanged")) {
            call {
                    interfaceName: InterfaceName,
                    changedProperties: Map<PropertyName, Variant>,
                    invalidatedProperties: List<PropertyName>
                ->

                onPropertiesChanged(
                    interfaceName,
                    changedProperties,
                    invalidatedProperties
                )
            }
        }
    }

    /**
     * Asynchronously sets a property to a pre-wrapped [Variant] value.
     *
     * @param interfaceName Interface that declares the property
     * @param propertyName Name of the property to set
     * @param value New value, wrapped in a [Variant]
     */
    suspend fun setAsync(interfaceName: InterfaceName, propertyName: PropertyName, value: Variant) =
        proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .getResult()

    /**
     * Reads all properties declared on the given interface.
     *
     * @return A map of property name to its current value
     */
    fun getAll(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllProperties().onInterface(interfaceName)

    /**
     * Asynchronously reads all properties declared on the given interface.
     *
     * @return A map of property name to its current value
     */
    suspend fun getAllAsync(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult()

    companion object {

        /**
         * Sets a property to a typed value, deducing serialization from the reified type [T].
         *
         * @param interfaceName Interface that declares the property
         * @param propertyName Name of the property to set
         * @param value New value
         * @param dontExpectReply When `true`, send the set without waiting for a reply
         */
        inline fun <reified T : Any> PropertiesProxy.set(
            interfaceName: InterfaceName,
            propertyName: PropertyName,
            value: T,
            dontExpectReply: Boolean = false
        ) {
            proxy.setProperty(
                interfaceName,
                propertyName,
                value,
                dontExpectReply = dontExpectReply
            )
        }

        /**
         * Asynchronously reads a property, deserializing it as the reified type [T].
         *
         * @param interfaceName Interface that declares the property
         * @param propertyName Name of the property to read
         * @return The current property value
         */
        suspend inline fun <reified T> PropertiesProxy.getAsync(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = proxy.getPropertyAsync(propertyName).onInterface(interfaceName).get()

        /**
         * Reads a property, deserializing it as the reified type [T].
         *
         * @param interfaceName Interface that declares the property
         * @param propertyName Name of the property to read
         * @return The current property value
         */
        inline fun <reified T> PropertiesProxy.get(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = proxy.getProperty(interfaceName, propertyName)

        /** The standard `org.freedesktop.DBus.Properties` interface name. */
        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

/**
 * Proxy for the ObjectManager interface, allows access to flows containing combinations of objects,
 * their interfaces, and their properties.
 */
class ObjectManagerProxy(
    /** The underlying proxy targeting the object that exports the ObjectManager interface. */
    val proxy: Proxy
) {
    private val state =
        MutableStateFlow(mapOf<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>>())

    /** A flow of the set of object paths currently known to the object manager. */
    val objects: Flow<List<ObjectPath>> = state.map { it.keys.toList() }

    init {
        proxy.onSignal(INTERFACE_NAME, SignalName("InterfacesAdded")) {
            call {
                    objectPath: ObjectPath,
                    interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
                ->
                state.update {
                    val map = it[objectPath].orEmpty() + interfacesAndProperties
                    it + (objectPath to map)
                }
            }
        }
        proxy.onSignal(INTERFACE_NAME, SignalName("InterfacesRemoved")) {
            call {
                    objectPath: ObjectPath,
                    interfaces: List<InterfaceName>
                ->
                state.update {
                    val map = it[objectPath].orEmpty() - interfaces.toSet()
                    it + (objectPath to map)
                }
            }
        }
        state.value = runCatching {
            getManagedObjects()
        }.getOrNull().orEmpty()
    }

    /** A flow of the interfaces exported by the object at [objectPath]. */
    fun interfacesFor(objectPath: ObjectPath): Flow<List<InterfaceName>> = state.map {
        it[objectPath]?.keys?.toList().orEmpty()
    }

    /** A flow of the object paths that export the given [interfaceName]. */
    fun objectsFor(interfaceName: InterfaceName): Flow<List<ObjectPath>> = state.map {
        it.filter { it.value.containsKey(interfaceName) }.keys.toList()
    }

    /** A flow of the interfaces and their property values for the object at [objectPath]. */
    fun objectData(objectPath: ObjectPath): Flow<Map<InterfaceName, Map<PropertyName, Variant>>> =
        state.map { it[objectPath].orEmpty() }

    /**
     * Synchronously queries the full set of managed objects, their interfaces, and properties.
     *
     * @return A map of object path to its interfaces and their property values
     */
    fun getManagedObjects(): Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>> =
        proxy.callMethod(INTERFACE_NAME, MethodName("GetManagedObjects")) { }

    companion object {
        /** The standard `org.freedesktop.DBus.ObjectManager` interface name. */
        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.ObjectManager")
    }
}
