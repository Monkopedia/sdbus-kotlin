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

import com.monkopedia.sdbus.Properties.PropertiesChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

interface Peer {
    fun ping(): Unit

    fun getMachineId(): String

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Peer")
    }
}

class PeerProxy(val proxy: Proxy) : Peer {
    override fun ping(): Unit = proxy.callMethod(Peer.INTERFACE_NAME, SignalName("Ping")) {}

    override fun getMachineId(): String =
        proxy.callMethod(Peer.INTERFACE_NAME, SignalName("GetMachineId")) {}
}

interface Properties {

    val propertiesChanged: Flow<PropertiesChanged>
    suspend fun setProperty(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant
    )

    suspend fun getAll(interfaceName: InterfaceName): Map<PropertyName, Variant>
    suspend fun getProperty(
        interfaceName: InterfaceName,
        propertyName: PropertyName
    ): AsyncPropertyGetter

    @Serializable
    data class PropertiesChanged(
        val intf: String,
        val changedProperties: Map<String, Variant>,
        val invalidatedProperties: List<String>
    )

    companion object {

        suspend inline fun <reified T : Any> Properties.set(
            interfaceName: InterfaceName,
            propertyName: PropertyName,
            value: T
        ) {
            setProperty(
                interfaceName,
                propertyName,
                Variant(value)
            )
        }

        suspend inline fun <reified T> Properties.get(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = getProperty(interfaceName, propertyName).get()

        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

// Proxy for properties
class PropertiesProxy(val proxy: Proxy) : Properties {

    override val propertiesChanged: Flow<PropertiesChanged> =
        proxy.signalFlow(Properties.INTERFACE_NAME, SignalName("PropertiesChanged")) {
            call(::PropertiesChanged)
        }

    override suspend fun setProperty(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant
    ) = proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
        .getResult()

    override suspend fun getAll(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult()

    override suspend fun getProperty(
        interfaceName: InterfaceName,
        propertyName: PropertyName
    ): AsyncPropertyGetter = proxy.getPropertyAsync(propertyName).onInterface(interfaceName)
}

/**
 * Proxy for the ObjectManager interface, allows access to flows containing combinations of objects,
 * their interfaces, and their properties.
 */
class ObjectManagerProxy(val proxy: Proxy) {
    private val state =
        MutableStateFlow(mapOf<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>>())

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

    fun interfacesFor(objectPath: ObjectPath): Flow<List<InterfaceName>> = state.map {
        it[objectPath]?.keys?.toList().orEmpty()
    }

    fun objectsFor(interfaceName: InterfaceName): Flow<List<ObjectPath>> = state.map {
        it.filter { it.value.containsKey(interfaceName) }.keys.toList()
    }

    fun objectData(objectPath: ObjectPath): Flow<Map<InterfaceName, Map<PropertyName, Variant>>> =
        state.map { it[objectPath].orEmpty() }

    fun getManagedObjects(): Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>> =
        proxy.callMethod(INTERFACE_NAME, MethodName("GetManagedObjects")) { }

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.ObjectManager")
    }
}
