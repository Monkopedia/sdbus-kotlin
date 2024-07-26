package com.monkopedia.sdbus

/**
 * Adds a declaration of methods, properties and signals of the object at a given interface
 *
 * @param vtable Individual instances of VTable item structures stored in a vector
 * @return Resource handle to release registration
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
 * Example 1:
 * ```
 * obj.addVTable(InterfaceName("com.monkopedia.foo")) {
 *   method(MethodName("Multiply")) {
 *     inputParamNames = listOf("a", "b")
 *     implementedAs(acall(this@MyAdaptor::slowMultiply) withContext Dispatchers.IO)
 *   }
 *   method(MethodName("Divide")) {
 *     call(this@MyAdaptor::divide)
 *   }
 *   prop(PropertyName("Name")) {
 *     with(this@MyAdaptor::name)
 *   }
 * }
 * ```
 *
 * Example 2:
 * ```
 * obj.addVTable(InterfaceName("org.freedesktop.two.DBus.Properties")) {
 *   method(MethodName("Get")) {
 *     inputParamNames = listOf("interface_name", "property_name")
 *     outputParamNames = listOf("value")
 *     acall(this@PropertiesAdaptor::`get`)
 *   }
 *   signal(SignalName("PropertiesChanged")) {
 *     with<String>("interface_name")
 *     with<Map<String, Variant>>("changed_properties")
 *     with<List<String>>("invalidated_properties")
 *   }
 * }
 * ```
 *
 * The function provides strong exception guarantee. The state of the object remains
 * unmodified in face of an exception.
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 * @see [com.monkopedia.sdbus.Resource]
 * @see [prop]
 * @see [method]
 * @see [signal]
 */
inline fun Object.addVTable(interfaceName: InterfaceName, builder: VTableBuilder.() -> Unit) =
    addVTable(interfaceName, buildList { VTableBuilder(this).builder() })

value class VTableBuilder(val items: MutableList<VTableItem>)

sealed interface VTableItem
