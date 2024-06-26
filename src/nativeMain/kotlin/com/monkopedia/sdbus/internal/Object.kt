@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.Flags
import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.InterfaceFlagsVTableItem
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodVTableItem
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyGetReply
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.PropertySetCall
import com.monkopedia.sdbus.PropertyVTableItem
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.SignalVTableItem
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.VTableItem
import com.monkopedia.sdbus.MethodCallback
import com.monkopedia.sdbus.PropertyGetCallback
import com.monkopedia.sdbus.PropertySetCallback
import com.monkopedia.sdbus.return_slot
import com.monkopedia.sdbus.return_slot_t
import com.monkopedia.sdbus.sdbusRequire
import com.monkopedia.sdbus.internal.Object.VTable.MethodItem
import com.monkopedia.sdbus.internal.Object.VTable.PropertyItem
import com.monkopedia.sdbus.internal.Object.VTable.SignalItem
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
import platform.posix.EINVAL
import sdbus.sd_bus_error
import sdbus.sd_bus_error_set
import sdbus.sd_bus_vtable

internal class Object(private val connection: IConnection, private val path: ObjectPath) :
    IObject {
    private class Allocs {
        var objManager: Slot? = null

        fun release() {
            objManager?.release()
            objManager = null
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
        checkObjectPath(path.value)
    }

    override fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>) {
        allocs.objManager = addVTable(interfaceName, vtable, return_slot)
    }

    override fun addVTable(
        interfaceName: InterfaceName,
        vtable: List<VTableItem>,
        return_slot: return_slot_t
    ): Slot = memScoped {
        checkInterfaceName(interfaceName.value)

        // 1st pass -- create vtable structure for internal sdbus-c++ purposes
        val internalVTable = createInternalVTable(interfaceName, vtable)

        // Return vtable wrapped in a Slot object
        val reference = Reference(internalVTable) {
            it.clear()
        }
        // 2nd pass -- from internal sdbus-c++ vtable, create vtable structure in format expected by underlying sd-bus library
        internalVTable.sdbusVTable = internalVTable.scope.createInternalSdBusVTable(internalVTable)

        // 3rd step -- register the vtable with sd-bus
        val slot = connection.addObjectVTable(
            path,
            internalVTable.interfaceName,
            internalVTable.sdbusVTable!!,
            internalVTable,
            return_slot
        )

        reference.freeAfter(slot)
    }

    override fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal =
        connection.createSignal(path, interfaceName, signalName)

    override fun createSignal(interfaceName: String, signalName: String): Signal =
        connection.createSignal(path.value, interfaceName, signalName)

    override fun emitSignal(message: Signal) {
        sdbusRequire(!message.isValid, "Invalid signal message provided", EINVAL)

        message.send()
    }

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        emitPropertiesChangedSignal(interfaceName, emptyList())
    }

    override fun emitPropertiesChangedSignal(interfaceName: String) {
        emitPropertiesChangedSignal(interfaceName, emptyList())
    }

    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ) {
        connection.emitPropertiesChangedSignal(path, interfaceName, propNames)
    }

    override fun emitPropertiesChangedSignal(interfaceName: String, propNames: List<PropertyName>) {
        connection.emitPropertiesChangedSignal(path.value, interfaceName, propNames)
    }

    override fun emitInterfacesAddedSignal() {
        connection.emitInterfacesAddedSignal(path)
    }

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        connection.emitInterfacesAddedSignal(path, interfaces)
    }

    override fun emitInterfacesRemovedSignal() {
        connection.emitInterfacesRemovedSignal(path)
    }

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        connection.emitInterfacesRemovedSignal(path, interfaces)
    }

    override fun addObjectManager() {
        allocs.objManager = connection.addObjectManager(path, return_slot)
    }

    override fun addObjectManager(t: return_slot_t): Slot =
        connection.addObjectManager(path, return_slot)

    override fun getConnection(): com.monkopedia.sdbus.IConnection = connection

    override fun getObjectPath(): ObjectPath = path

    override fun getCurrentlyProcessedMessage(): Message =
        connection.getCurrentlyProcessedMessage()

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
        var obj: Object? = null

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
                signal.signature!!,
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
            method: VTable.MethodItem,
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
            signal: VTable.SignalItem,
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
            property: VTable.PropertyItem,
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
