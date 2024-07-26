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
        userData: Any?,
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

        fun getPseudoConnectionInstance(): InternalConnection = instance ?: createPseudoConnection().also {
            instance = it
        }
    }
}