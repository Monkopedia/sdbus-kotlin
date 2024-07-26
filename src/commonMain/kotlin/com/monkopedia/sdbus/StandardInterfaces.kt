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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface ProxyHolder {
    val proxy: Proxy
}

interface PeerProxy : ProxyHolder {
    fun ping(): Unit = proxy.callMethod(INTERFACE_NAME, SignalName("Ping")) {}

    fun getMachineId(): String = proxy.callMethod(INTERFACE_NAME, SignalName("GetMachineId")) {}

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Peer")
    }
}

// Proxy for properties
interface PropertiesProxy : ProxyHolder {

    fun registerPropertiesProxy() {
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

    fun onPropertiesChanged(
        interfaceName: InterfaceName,
        changedProperties: Map<PropertyName, Variant>,
        invalidatedProperties: List<PropertyName>
    ) = Unit

    suspend fun setAsync(interfaceName: InterfaceName, propertyName: PropertyName, value: Variant) =
        proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .getResult()

    fun getAll(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllProperties().onInterface(interfaceName)

    suspend fun getAllAsync(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult()

    companion object {

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

        suspend inline fun <reified T> PropertiesProxy.getAsync(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = proxy.getPropertyAsync(propertyName).onInterface(interfaceName).get()

        inline fun <reified T> PropertiesProxy.get(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = proxy.getProperty(interfaceName, propertyName)

        val INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
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
