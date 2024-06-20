@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.header.Flags
import com.monkopedia.sdbus.header.IObject
import com.monkopedia.sdbus.header.InterfaceFlagsVTableItem
import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.MethodCall
import com.monkopedia.sdbus.header.MethodName
import com.monkopedia.sdbus.header.MethodVTableItem
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PropertyGetReply
import com.monkopedia.sdbus.header.PropertyName
import com.monkopedia.sdbus.header.PropertySetCall
import com.monkopedia.sdbus.header.PropertyVTableItem
import com.monkopedia.sdbus.header.SDBUS_THROW_ERROR_IF
import com.monkopedia.sdbus.header.Signal
import com.monkopedia.sdbus.header.SignalName
import com.monkopedia.sdbus.header.SignalVTableItem
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.VTableItem
import com.monkopedia.sdbus.header.method_callback
import com.monkopedia.sdbus.header.property_get_callback
import com.monkopedia.sdbus.header.property_set_callback
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.header.return_slot_t
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


class Object(private val connection_: IConnection, private val objectPath_: ObjectPath) : IObject {
    private class Allocs {
        var objManager: Any? = null

        fun unregister() {
            objManager = null
        }
    }

    private val allocs = Allocs()
    private val cleaner = createCleaner(allocs) {
        it.unregister()
    }

    override fun unregister() {
        allocs.unregister()
    }

    init {
        SDBUS_CHECK_OBJECT_PATH(objectPath_.value);
    }

    override fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>) {
        allocs.objManager = addVTable(interfaceName, vtable, return_slot)
    }

    override fun addVTable(
        interfaceName: InterfaceName,
        vtable: List<VTableItem>,
        return_slot: return_slot_t
    ): Slot = memScoped {
        SDBUS_CHECK_INTERFACE_NAME(interfaceName.value);

        // 1st pass -- create vtable structure for internal sdbus-c++ purposes
        val internalVTable = createInternalVTable(interfaceName, vtable)

        // Return vtable wrapped in a Slot object
        val reference = Reference(internalVTable) {
            it.clear()
        }
        // 2nd pass -- from internal sdbus-c++ vtable, create vtable structure in format expected by underlying sd-bus library
        internalVTable.sdbusVTable = internalVTable.scope.createInternalSdBusVTable(internalVTable);

        // 3rd step -- register the vtable with sd-bus
        val slot = connection_.addObjectVTable(
            objectPath_,
            internalVTable.interfaceName,
            internalVTable.sdbusVTable!!,
            internalVTable,
            return_slot
        );

        reference.freeAfter(slot)
    }

    override fun createSignal(
        interfaceName: InterfaceName,
        signalName: SignalName
    ): Signal {
        return connection_.createSignal(objectPath_, interfaceName, signalName)
    }

    override fun createSignal(interfaceName: String, signalName: String): Signal {
        return connection_.createSignal(objectPath_.value, interfaceName, signalName)
    }

    override fun emitSignal(message: Signal) {
        SDBUS_THROW_ERROR_IF(!message.isValid(), "Invalid signal message provided", EINVAL);

        message.send();
    }

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        emitPropertiesChangedSignal(interfaceName, emptyList());
    }

    override fun emitPropertiesChangedSignal(interfaceName: String) {
        emitPropertiesChangedSignal(interfaceName, emptyList());
    }

    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ) {
        connection_.emitPropertiesChangedSignal(objectPath_, interfaceName, propNames);
    }

    override fun emitPropertiesChangedSignal(interfaceName: String, propNames: List<PropertyName>) {
        connection_.emitPropertiesChangedSignal(objectPath_.value, interfaceName, propNames);
    }

    override fun emitInterfacesAddedSignal() {
        connection_.emitInterfacesAddedSignal(objectPath_)
    }

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        connection_.emitInterfacesAddedSignal(objectPath_, interfaces)
    }

    override fun emitInterfacesRemovedSignal() {
        connection_.emitInterfacesRemovedSignal(objectPath_)
    }

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        connection_.emitInterfacesRemovedSignal(objectPath_, interfaces)
    }

    override fun addObjectManager() {
        allocs.objManager = connection_.addObjectManager(objectPath_, return_slot)
    }

    override fun addObjectManager(t: return_slot_t): Slot {
        return connection_.addObjectManager(objectPath_, return_slot)
    }

    override fun getConnection(): com.monkopedia.sdbus.header.IConnection {
        return connection_
    }

    override fun getObjectPath(): ObjectPath {
        return objectPath_
    }

    override fun getCurrentlyProcessedMessage(): Message {
        return connection_.getCurrentlyProcessedMessage()
    }

    //    private:
    // A vtable record comprising methods, signals, properties, flags.
    // Once created, it cannot be modified. Only new vtables records can be added.
    // An interface can have any number of vtables attached to it, not only one.
    class VTable(
        val interfaceName: InterfaceName,
    ) {
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

        init {
            println("Alloc vtable")
        }

        fun clear() {
            println("Clear vtable")
            scope.clear()
            obj = null
        }

        data class MethodItem(
            val name: MethodName,
            val inputSignature: Signature,
            val outputSignature: Signature,
            val paramNames: String,
            val callback: method_callback,
            val flags: Flags,
        )

        data class SignalItem(
            val name: SignalName,
            val signature: Signature,
            val paramNames: String,
            val flags: Flags,
        );

        data class PropertyItem(
            val name: PropertyName,
            val signature: Signature,
            val getCallback: property_get_callback?,
            val setCallback: property_set_callback?,
            val flags: Flags,
        )
    };

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

        internalVTable.obj = this;

        return internalVTable;

    }

    fun writeInterfaceFlagsToVTable(flags: InterfaceFlagsVTableItem, vtable: VTable) {
        vtable.interfaceFlags = flags.flags;
    }

    fun writeMethodRecordToVTable(method: MethodVTableItem, vtable: VTable) {
        SDBUS_CHECK_MEMBER_NAME(method.name.value)

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
        );

    }

    fun writeSignalRecordToVTable(signal: SignalVTableItem, vtable: VTable) {
        SDBUS_CHECK_MEMBER_NAME(signal.name.value);

        vtable.signals.add(
            SignalItem(
                signal.name,
                signal.signature!!,
                paramNamesToString(signal.paramNames),
                signal.flags
            )
        );

    }

    fun writePropertyRecordToVTable(property: PropertyVTableItem, vtable: VTable) {
        SDBUS_CHECK_MEMBER_NAME(property.name.value);

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

        startSdBusVTable(vtable.interfaceFlags, sdbusVTable);
        for (methodItem in vtable.methods)
            writeMethodRecordToSdBusVTable(methodItem, sdbusVTable);
        for (signalItem in vtable.signals)
            writeSignalRecordToSdBusVTable(signalItem, sdbusVTable);
        for (propertyItem in vtable.properties)
            writePropertyRecordToSdBusVTable(propertyItem, sdbusVTable);
        return finalizeSdBusVTable(sdbusVTable);
    }

    companion object {
        fun AutofreeScope.startSdBusVTable(
            interfaceFlags: Flags,
            vtable: MutableList<CValue<sd_bus_vtable>>
        ) {
            val vtableItem = createSdBusVTableStartItem(interfaceFlags.toSdBusInterfaceFlags());
            vtable.add(vtableItem);
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
            );
            vtable.add(vtableItem);
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
            );
            vtable.add(vtableItem);
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
            vtable.add(vtableItem);
        }

        fun AutofreeScope.finalizeSdBusVTable(vtable: MutableList<CValue<sd_bus_vtable>>): CArrayPointer<sd_bus_vtable> {
            vtable.add(createSdBusVTableEndItem());
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

        fun paramNamesToString(paramNames: List<String>): String {
            return paramNames.joinToString("\u0000") + "\u0000"

        }

        val sdbus_method_callback = staticCFunction { sdbusMessage: CPointer<sd_bus_message>?,
                                                      userData: COpaquePointer?,
                                                      retError: CPointer<sd_bus_error>? ->
            val vtable = userData?.asStableRef<Any>()?.get() as? VTable
            assert(vtable != null);
            assert(vtable?.obj != null);
            val ok = invokeHandlerAndCatchErrors(retError) {
                val message = MethodCall(
                    sdbusMessage!!,
                    vtable!!.obj!!.connection_.getSdBusInterface()
                )

                val methodItem = findMethod(vtable, message.getMemberName()!!);
                assert(methodItem != null);

                methodItem!!.callback(message);
            }

            if (ok) 1 else -1
        }

        val sdbus_property_get_callback = staticCFunction { bus: CPointer<sd_bus>?,
                                                            objectPath: CPointer<ByteVar>?,
                                                            intf: CPointer<ByteVar>?,
                                                            property: CPointer<ByteVar>?,
                                                            sdbusReply: CPointer<sd_bus_message>?,
                                                            userData: COpaquePointer?,
                                                            retError: CPointer<sd_bus_error>?
            ->
            val vtable = userData?.asStableRef<Any>()?.get() as? VTable
            assert(vtable != null);
            assert(vtable!!.obj != null);
            assert(property != null);

            val propertyItem = findProperty(vtable, property!!.toKString())
            assert(propertyItem != null);
            val ok = invokeHandlerAndCatchErrors(retError) {

                // Getter may be empty - the case of "write-only" property
                if (propertyItem!!.getCallback == null) {
                    sd_bus_error_set(
                        retError,
                        "org.freedesktop.DBus.Error.Failed",
                        "Cannot read property as it is write-only"
                    );
                    return@staticCFunction 1;
                }
                val reply = PropertyGetReply(
                    sdbusReply!!,
                    vtable.obj!!.connection_.getSdBusInterface()
                )

                propertyItem.getCallback!!(reply);
            }

            if (ok) 1 else -1;
        }

        val sdbus_property_set_callback = staticCFunction { bus: CPointer<sd_bus>?,
                                                            objectPath: CPointer<ByteVar>?,
                                                            intf: CPointer<ByteVar>?,
                                                            property: CPointer<ByteVar>?,
                                                            sdbusValue: CPointer<sd_bus_message>?,
                                                            userData: COpaquePointer?,
                                                            retError: CPointer<sd_bus_error>?
            ->
            val vtable = userData?.asStableRef<Any>()?.get() as? VTable
            assert(vtable != null);
            assert(vtable!!.obj != null);

            val propertyItem = findProperty(vtable, property!!.toKString())
            assert(propertyItem != null);
            assert(propertyItem!!.setCallback != null);
            val ok = invokeHandlerAndCatchErrors(retError) {
                val value = PropertySetCall(
                    sdbusValue!!,
                    vtable.obj!!.connection_.getSdBusInterface()
                )
                propertyItem.setCallback!!(value);
            };

            if (ok) 1 else -1
        }
    }

//    private:
//    sdbus::internal::IConnection& connection_;
//    ObjectPath objectPath_;
//    std::vector<Slot> vtables_;
//    Slot objectManagerSlot_;
};
