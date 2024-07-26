package com.monkopedia.sdbus


/*!
 * @brief Adds a declaration of methods, properties and signals of the object at a given interface
 *
 * @param[in] vtable Individual instances of VTable item structures stored in a vector
 * @return VTableAdder high-level helper class
 *
 * This method is used to declare attributes for the object under the given interface.
 * Parameter `vtable' represents a vtable definition that may contain method declarations
 * (using MethodVTableItem struct), property declarations (using PropertyVTableItem
 * struct), signal declarations (using SignalVTableItem struct), or global interface
 * flags (using InterfaceFlagsVTableItem struct).
 *
 * An interface can have any number of vtables attached to it.
 *
 * Consult manual pages for the underlying `sd_bus_add_object_vtable` function for more information.
 *
 * The method can be called at any time during object's lifetime.
 *
 * When called like `addVTable(vtable).forInterface(interface)`, then an internal registration
 * slot is created for that vtable and its lifetime is tied to the lifetime of the Object instance.
 * When called like `addVTable(items...).forInterface(interface, sdbus::return_slot)`, then an internal
 * registration slot is created for the vtable and is returned to the caller. Keeping the slot means
 * keep the registration "alive". Destroying the slot means that the vtable is not needed anymore,
 * and the vtable gets removed from the object. This allows for "dynamic" object API where vtables
 * can be added or removed by the user at runtime.
 *
 * The function provides strong exception guarantee. The state of the object remains
 * unmodified in face of an exception.
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IObject.addVTable(interfaceName: InterfaceName, builder: VTableBuilder.() -> Unit) =
    addVTable(interfaceName, buildList { VTableBuilder(this).builder() })

inline fun IObject.addVTable(interfaceName: String, builder: VTableBuilder.() -> Unit) =
    addVTable(InterfaceName(interfaceName), builder)

value class VTableBuilder(val items: MutableList<VTableItem>)

sealed interface VTableItem
