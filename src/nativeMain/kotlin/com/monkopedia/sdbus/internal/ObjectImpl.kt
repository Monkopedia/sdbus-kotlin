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
@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.Flags
import com.monkopedia.sdbus.InterfaceFlagsVTableItem
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodCallback
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodVTableItem
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyGetCallback
import com.monkopedia.sdbus.PropertyGetReply
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.PropertySetCall
import com.monkopedia.sdbus.PropertySetCallback
import com.monkopedia.sdbus.PropertyVTableItem
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.SignalVTableItem
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.VTableItem
import com.monkopedia.sdbus.internal.ObjectImpl.VTable.MethodItem
import com.monkopedia.sdbus.internal.ObjectImpl.VTable.PropertyItem
import com.monkopedia.sdbus.internal.ObjectImpl.VTable.SignalItem
import com.monkopedia.sdbus.sdbusRequire
import com.monkopedia.sdbus.toSdBusInterfaceFlags
import com.monkopedia.sdbus.toSdBusMethodFlags
import com.monkopedia.sdbus.toSdBusPropertyFlags
import com.monkopedia.sdbus.toSdBusSignalFlags
import com.monkopedia.sdbus.toSdBusWritablePropertyFlags
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.Arena
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import platform.posix.EINVAL
import sdbus.sd_bus_error
import sdbus.sd_bus_error_set
import sdbus.sd_bus_vtable

internal class ObjectImpl(
    override val connection: InternalConnection,
    override val objectPath: ObjectPath
) : Object {
    private class Allocs {
        var objManagers = MutableStateFlow<List<Resource>>(emptyList())

        fun release() {
            objManagers.value.forEach { it.release() }
            objManagers.value = emptyList()
        }
    }

    private val allocs = Allocs()
    private val cleaner = createCleaner(allocs) {
        it.release()
    }

    override fun release() {
        allocs.release()
    }

    init {
        checkObjectPath(objectPath.value)
    }

    override fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): Resource =
        memScoped {
            checkInterfaceName(interfaceName.value)

            // 1st pass -- create vtable structure for internal sdbus-kotlin purposes
            val internalVTable = createInternalVTable(interfaceName, vtable)

            // Return vtable wrapped in a Slot object
            val reference = Reference(internalVTable) {
                it.clear()
            }
            // 2nd pass -- from internal sdbus-kotlin vtable, create vtable structure in format expected by underlying sd-bus library
            internalVTable.sdbusVTable =
                internalVTable.scope.createInternalSdBusVTable(internalVTable)

            // 3rd step -- register the vtable with sd-bus
            val slot = connection.addObjectVTable(
                objectPath,
                internalVTable.interfaceName,
                internalVTable.sdbusVTable!!,
                internalVTable
            )

            reference.freeAfter(slot).also { slot ->
                allocs.objManagers.update { it + slot }
            }
        }

    override fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal =
        connection.createSignal(objectPath, interfaceName, signalName)

    override fun emitSignal(message: Signal) {
        sdbusRequire(!message.isValid, "Invalid signal message provided", EINVAL)

        message.send()
    }

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        emitPropertiesChangedSignal(interfaceName, emptyList())
    }

    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ) {
        connection.emitPropertiesChangedSignal(objectPath, interfaceName, propNames)
    }

    override fun emitInterfacesAddedSignal() {
        connection.emitInterfacesAddedSignal(objectPath)
    }

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        connection.emitInterfacesAddedSignal(objectPath, interfaces)
    }

    override fun emitInterfacesRemovedSignal() {
        connection.emitInterfacesRemovedSignal(objectPath)
    }

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        connection.emitInterfacesRemovedSignal(objectPath, interfaces)
    }

    override fun addObjectManager(): Resource =
        connection.addObjectManager(objectPath).also { slot ->
            allocs.objManagers.update { it + slot }
        }

    override val currentlyProcessedMessage: Message
        get() = connection.currentlyProcessedMessage

    //    private:
    // A vtable record comprising methods, signals, properties, flags.
    // Once created, it cannot be modified. Only new vtables records can be added.
    // An interface can have any number of vtables attached to it, not only one.
    class VTable(val interfaceName: InterfaceName) {
        internal val scope = Arena()
        fun doSorting() {
            methods.sortBy { it.name.value }
            signals.sortBy { it.name.value }
            properties.sortBy { it.name.value }
        }

        var interfaceFlags: Flags = Flags()

        // Array of method records sorted by method name
        val methods: MutableList<MethodItem> = mutableListOf()

        // Array of signal records sorted by signal name
        val signals: MutableList<SignalItem> = mutableListOf()

        // Array of signal records sorted by signal name
        val properties: MutableList<PropertyItem> = mutableListOf()

        // VTable structure in format required by sd-bus API
        var sdbusVTable: CArrayPointer<sd_bus_vtable>? = null

        // Back-reference to the owning object from sd-bus callback handlers
        var obj: ObjectImpl? = null

        fun clear() {
            scope.clear()
            obj = null
        }

        data class MethodItem(
            val name: MethodName,
            val inputSignature: Signature,
            val outputSignature: Signature,
            val paramNames: String,
            val callback: MethodCallback,
            val flags: Flags
        )

        data class SignalItem(
            val name: SignalName,
            val signature: Signature,
            val paramNames: String,
            val flags: Flags
        )

        data class PropertyItem(
            val name: PropertyName,
            val signature: Signature,
            val getCallback: PropertyGetCallback?,
            val setCallback: PropertySetCallback?,
            val flags: Flags
        )
    }

    fun createInternalVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): VTable {
        val internalVTable = VTable(interfaceName)

        for (vtableItem in vtable) {
            when (vtableItem) {
                is InterfaceFlagsVTableItem ->
                    writeInterfaceFlagsToVTable(vtableItem, internalVTable)

                is MethodVTableItem -> writeMethodRecordToVTable(vtableItem, internalVTable)
                is PropertyVTableItem -> writePropertyRecordToVTable(vtableItem, internalVTable)
                is SignalVTableItem -> writeSignalRecordToVTable(vtableItem, internalVTable)
            }
        }

        // Sort arrays so we can do fast searching for an item in sd-bus callback handlers
        internalVTable.doSorting()

        internalVTable.obj = this

        return internalVTable
    }

    fun writeInterfaceFlagsToVTable(flags: InterfaceFlagsVTableItem, vtable: VTable) {
        vtable.interfaceFlags = flags.flags
    }

    fun writeMethodRecordToVTable(method: MethodVTableItem, vtable: VTable) {
        checkMemberName(method.name.value)

        vtable.methods.add(
            MethodItem(
                method.name,
                method.inputSignature!!,
                method.outputSignature!!,
                paramNamesToString(method.inputParamNames) +
                    paramNamesToString(method.outputParamNames),
                method.callbackHandler!!,
                method.flags
            )
        )
    }

    fun writeSignalRecordToVTable(signal: SignalVTableItem, vtable: VTable) {
        checkMemberName(signal.name.value)

        vtable.signals.add(
            SignalItem(
                signal.name,
                signal.signature,
                paramNamesToString(signal.paramNames),
                signal.flags
            )
        )
    }

    fun writePropertyRecordToVTable(property: PropertyVTableItem, vtable: VTable) {
        checkMemberName(property.name.value)

        vtable.properties.add(
            PropertyItem(
                property.name,
                property.signature!!,
                property.getter,
                property.setter,
                property.flags
            )
        )
    }

    fun AutofreeScope.createInternalSdBusVTable(vtable: VTable): CArrayPointer<sd_bus_vtable> {
        val sdbusVTable = mutableListOf<CValue<sd_bus_vtable>>()

        startSdBusVTable(vtable.interfaceFlags, sdbusVTable)
        for (methodItem in vtable.methods) {
            writeMethodRecordToSdBusVTable(methodItem, sdbusVTable)
        }
        for (signalItem in vtable.signals) {
            writeSignalRecordToSdBusVTable(signalItem, sdbusVTable)
        }
        for (propertyItem in vtable.properties) {
            writePropertyRecordToSdBusVTable(propertyItem, sdbusVTable)
        }
        return finalizeSdBusVTable(sdbusVTable)
    }

    companion object {
        fun startSdBusVTable(interfaceFlags: Flags, vtable: MutableList<CValue<sd_bus_vtable>>) {
            val vtableItem = createSdBusVTableStartItem(interfaceFlags.toSdBusInterfaceFlags())
            vtable.add(vtableItem)
        }

        fun AutofreeScope.writeMethodRecordToSdBusVTable(
            method: MethodItem,
            vtable: MutableList<CValue<sd_bus_vtable>>
        ) {
            val vtableItem = createSdBusVTableMethodItem(
                method.name.value.cstr.getPointer(this),
                method.inputSignature.value.cstr.getPointer(this),
                method.outputSignature.value.cstr.getPointer(this),
                (method.paramNames + "\u0000").cstr.getPointer(this),
                sdbus_method_callback,
                method.flags.toSdBusMethodFlags()
            )
            vtable.add(vtableItem)
        }

        fun AutofreeScope.writeSignalRecordToSdBusVTable(
            signal: SignalItem,
            vtable: MutableList<CValue<sd_bus_vtable>>
        ) {
            val vtableItem = createSdBusVTableSignalItem(
                signal.name.value.cstr.getPointer(this),
                signal.signature.value.cstr.getPointer(this),
                signal.paramNames.cstr.getPointer(this),
                signal.flags.toSdBusSignalFlags()
            )
            vtable.add(vtableItem)
        }

        fun AutofreeScope.writePropertyRecordToSdBusVTable(
            property: PropertyItem,
            vtable: MutableList<CValue<sd_bus_vtable>>
        ) {
            val vtableItem = if (property.setCallback == null) {
                createSdBusVTableReadOnlyPropertyItem(
                    property.name.value.cstr.getPointer(this),
                    property.signature.value.cstr.getPointer(this),
                    sdbus_property_get_callback,
                    property.flags.toSdBusPropertyFlags()
                )
            } else {
                createSdBusVTableWritablePropertyItem(
                    property.name.value.cstr.getPointer(this),
                    property.signature.value.cstr.getPointer(this),
                    sdbus_property_get_callback,
                    sdbus_property_set_callback,
                    property.flags.toSdBusWritablePropertyFlags()
                )
            }
            vtable.add(vtableItem)
        }

        fun AutofreeScope.finalizeSdBusVTable(
            vtable: MutableList<CValue<sd_bus_vtable>>
        ): CArrayPointer<sd_bus_vtable> {
            vtable.add(createSdBusVTableEndItem())
            return allocArray<sd_bus_vtable>(vtable.size) { i ->
                vtable[i].place(ptr)
            }
        }

        fun findMethod(vtable: VTable, methodName: String): MethodItem? {
            val index = vtable.methods.binarySearch { it.name.value.compareTo(methodName) }
            if (index < 0) {
                return null
            }
            return vtable.methods[index]
        }

        fun findProperty(vtable: VTable, propertyName: String): PropertyItem? {
            val index = vtable.properties.binarySearch { it.name.value.compareTo(propertyName) }
            if (index < 0) {
                return null
            }
            return vtable.properties[index]
        }

        fun paramNamesToString(paramNames: List<String>): String =
            paramNames.joinToString("\u0000") + "\u0000"

        val sdbus_method_callback = staticCFunction {
                sdbusMessage: CPointer<sd_bus_message>?,
                userData: COpaquePointer?,
                retError: CPointer<sd_bus_error>?
            ->
            val vtable = userData?.asStableRef<Any>()?.get() as? VTable
            assert(vtable != null)
            assert(vtable?.obj != null)
            val ok = invokeHandlerAndCatchErrors(retError) {
                val message = MethodCall(
                    sdbusMessage!!,
                    vtable!!.obj!!.connection.getSdBusInterface()
                )

                val methodItem = findMethod(vtable, message.getMemberName()!!)
                assert(methodItem != null)

                methodItem!!.callback(message)
            }

            if (ok) 1 else -1
        }

        val sdbus_property_get_callback = staticCFunction {
                bus: CPointer<sd_bus>?,
                objectPath: CPointer<ByteVar>?,
                intf: CPointer<ByteVar>?,
                property: CPointer<ByteVar>?,
                sdbusReply: CPointer<sd_bus_message>?,
                userData: COpaquePointer?,
                retError: CPointer<sd_bus_error>?
            ->
            val vtable = userData?.asStableRef<Any>()?.get() as? VTable
            assert(vtable != null)
            assert(vtable!!.obj != null)
            assert(property != null)

            val propertyItem = findProperty(vtable, property!!.toKString())
            assert(propertyItem != null)
            val ok = invokeHandlerAndCatchErrors(retError) {

                // Getter may be empty - the case of "write-only" property
                if (propertyItem!!.getCallback == null) {
                    sd_bus_error_set(
                        retError,
                        "org.freedesktop.DBus.Error.Failed",
                        "Cannot read property as it is write-only"
                    )
                    return@staticCFunction 1
                }
                val reply = PropertyGetReply(
                    sdbusReply!!,
                    vtable.obj!!.connection.getSdBusInterface()
                )

                propertyItem.getCallback!!(reply)
            }

            if (ok) 1 else -1
        }

        val sdbus_property_set_callback = staticCFunction {
                bus: CPointer<sd_bus>?,
                objectPath: CPointer<ByteVar>?,
                intf: CPointer<ByteVar>?,
                property: CPointer<ByteVar>?,
                sdbusValue: CPointer<sd_bus_message>?,
                userData: COpaquePointer?,
                retError: CPointer<sd_bus_error>?
            ->
            val vtable = userData?.asStableRef<Any>()?.get() as? VTable
            assert(vtable != null)
            assert(vtable!!.obj != null)

            val propertyItem = findProperty(vtable, property!!.toKString())
            assert(propertyItem != null)
            assert(propertyItem!!.setCallback != null)
            val ok = invokeHandlerAndCatchErrors(retError) {
                val value = PropertySetCall(
                    sdbusValue!!,
                    vtable.obj!!.connection.getSdBusInterface()
                )
                propertyItem.setCallback!!(value)
            }

            if (ok) 1 else -1
        }
    }
}
