package com.monkopedia.sdbus

import kotlin.jvm.JvmInline

/**
 * Adds a declaration of methods, properties and signals of the object at a given interface
 *
 * @param vtable Individual instances of VTable item structures stored in a vector
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
 * The function provides strong exception guarantee. The state of the object remains
 * unmodified in face of an exception.
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 * @see [com.monkopedia.sdbus.Resource]
 */
inline fun Object.addVTable(interfaceName: InterfaceName, builder: VTableBuilder.() -> Unit) =
    addVTable(interfaceName, buildList { VTableBuilder(this).builder() })

@JvmInline
value class VTableBuilder(val items: MutableList<VTableItem>)

sealed interface VTableItem
