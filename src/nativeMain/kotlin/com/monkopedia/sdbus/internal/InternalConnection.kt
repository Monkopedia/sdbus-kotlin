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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PlainMessage
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.pseudoConnection
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_vtable

internal interface InternalConnection : com.monkopedia.sdbus.Connection {

    fun getSdBusInterface(): ISdBus

    fun addObjectVTable(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        vtable: CValuesRef<sd_bus_vtable>,
        userData: Any?
    ): Reference<*>

    fun createPlainMessage(): PlainMessage
    fun createMethodCall(
        destination: ServiceName,
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall

    fun createMethodCall(
        destination: String,
        objectPath: String,
        interfaceName: String,
        methodName: String
    ): MethodCall

    fun createSignal(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        signalName: SignalName
    ): Signal

    fun createSignal(objectPath: String, interfaceName: String, signalName: String): Signal

    fun callMethod(message: MethodCall, timeout: ULong): MethodReply
    fun callMethod(
        message: MethodCall,
        callback: sd_bus_message_handler_t,
        userData: Any?,
        timeout: ULong
    ): Resource

    fun emitPropertiesChangedSignal(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    )

    fun emitPropertiesChangedSignal(
        objectPath: String,
        interfaceName: String,
        propNames: List<PropertyName>
    )

    fun emitInterfacesAddedSignal(objectPath: ObjectPath)
    fun emitInterfacesAddedSignal(objectPath: ObjectPath, interfaces: List<InterfaceName>)
    fun emitInterfacesRemovedSignal(objectPath: ObjectPath)
    fun emitInterfacesRemovedSignal(objectPath: ObjectPath, interfaces: List<InterfaceName>)

    fun registerSignalHandler(
        sender: String,
        objectPath: String,
        interfaceName: String,
        signalName: String,
        callback: sd_bus_message_handler_t,
        userData: Any?
    ): Resource

    companion object {
        fun createPseudoConnection(): InternalConnection {
            val intf = SdBus()
            return pseudoConnection(intf)
        }

        private var instance: InternalConnection? = null

        fun getPseudoConnectionInstance(): InternalConnection =
            instance ?: createPseudoConnection().also {
                instance = it
            }
    }
}
