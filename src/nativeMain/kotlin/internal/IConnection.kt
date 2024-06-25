@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.MethodCall
import com.monkopedia.sdbus.header.MethodName
import com.monkopedia.sdbus.header.MethodReply
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PlainMessage
import com.monkopedia.sdbus.header.PropertyName
import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.Signal
import com.monkopedia.sdbus.header.SignalName
import com.monkopedia.sdbus.header.return_slot_t
import com.monkopedia.sdbus.internal.Connection.Companion.pseudoConnection
import header.ISdBus
import header.Resource
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_vtable

typealias Slot = Resource

internal interface IConnection : com.monkopedia.sdbus.header.IConnection {

    fun getSdBusInterface(): ISdBus

    fun addObjectVTable(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        vtable: CValuesRef<sd_bus_vtable>,
        userData: Any?,
        return_slot: return_slot_t
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

    fun createSignal(
        objectPath: String,
        interfaceName: String,
        signalName: String
    ): Signal

    fun callMethod(message: MethodCall, timeout: ULong): MethodReply
    fun callMethod(
        message: MethodCall,
        callback: sd_bus_message_handler_t,
        userData: Any?,
        timeout: ULong,
        return_slot: return_slot_t
    ): Slot

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
        userData: Any?,
        return_slot: return_slot_t
    ): Slot

    companion object {
        fun createPseudoConnection(): IConnection {
            val intf = SdBus()
            return  pseudoConnection(intf)
        }

        private var instance: IConnection? = null

        fun getPseudoConnectionInstance(): IConnection {
            return instance ?: createPseudoConnection().also { instance = it }
        }
    }
};

