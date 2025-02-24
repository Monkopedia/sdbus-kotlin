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
package com.monkopedia.sdbus

/********************************************/
/**
 * Object class represents a D-Bus object instance identified by a specific object path.
 * D-Bus object provides its interfaces, methods, signals and properties on a bus
 * identified by a specific bus name.
 *
 * All Object member methods throw [com.monkopedia.sdbus.Error] in case of D-Bus or sdbus-kotlin error.
 * The Object class has been designed as thread-aware. However, the operation of
 * creating and sending asynchronous method replies, as well as creating and emitting
 * signals, is thread-safe by design.
 *
 ***********************************************/
interface Object : Resource {

    /**
     * Emits PropertyChanged signal for specified properties under a given interface of this object path
     *
     * @param interfaceName Name of an interface that properties belong to
     * @param propNames Names of properties that will be included in the PropertiesChanged signal
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitPropertiesChangedSignal(interfaceName: InterfaceName, propNames: List<PropertyName>)

    /**
     * Emits PropertyChanged signal for all properties on a given interface of this object pat
     *
     * @param interfaceName Name of an interface
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitPropertiesChangedSignal(interfaceName: InterfaceName)

    /**
     * Emits InterfacesAdded signal on this object path
     *
     * This emits an InterfacesAdded signal on this object path, by iterating all registered
     * interfaces on the path. All properties are queried and included in the signal.
     * This call is equivalent to emitInterfacesAddedSignal() with an explicit list of
     * registered interfaces. However, unlike emitInterfacesAddedSignal(interfaces), this
     * call can figure out the list of supported interfaces itself. Furthermore, it properly
     * adds the builtin org.freedesktop.DBus.* interfaces.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitInterfacesAddedSignal()

    /**
     * Emits InterfacesAdded signal on this object path
     *
     * This emits an InterfacesAdded signal on this object path with explicitly provided list
     * of registered interfaces.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>)

    /**
     * Emits InterfacesRemoved signal on this object path
     *
     * This is like sd_bus_emit_object_added(), but emits an InterfacesRemoved signal on this
     * object path. This only includes any registered interfaces but skips the properties.
     * This function shall be called (just) before destroying the object.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitInterfacesRemovedSignal()

    /**
     * Emits InterfacesRemoved signal on this object path
     *
     * This emits an InterfacesRemoved signal on the given path with explicitly provided list
     * of registered interfaces.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>)

    /**
     * Adds an ObjectManager interface at the path of this D-Bus object
     *
     * @return [Resource] handle owning the registration
     *
     * Creates an ObjectManager interface at the specified object path on
     * the connection. This is a convenient way to interrogate a connection
     * to see what objects it has.
     *
     * The lifetime of the ObjectManager interface is bound to the lifetime
     * of the returned resource instance.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun addObjectManager(): Resource

    /**
     * Provides D-Bus connection used by the object
     *
     * @return Reference to the D-Bus connection
     */
    val connection: Connection

    /**
     * Returns object path of the underlying DBus object
     */
    val objectPath: ObjectPath

    /**
     * Provides access to the currently processed D-Bus message
     *
     * This method provides access to the currently processed incoming D-Bus message.
     * "Currently processed" means that the registered callback handler(s) for that message
     * are being invoked. This method is meant to be called from within a callback handler
     * (e.g. from a D-Bus signal handler, or async method reply handler, etc.). In such a case it is
     * guaranteed to return a valid D-Bus message instance for which the handler is called.
     * If called from other contexts/threads, it may return a valid or invalid message, depending
     * on whether a message was processed or not at the time of the call.
     *
     * @return Currently processed D-Bus message
     */
    val currentlyProcessedMessage: Message

    /**
     * Adds a declaration of methods, properties and signals of the object at a given interface
     *
     * @param interfaceName Name of an interface the the vtable is registered for
     * @param vtable A list of individual descriptions in the form of VTable item instances
     *
     * This method is used to declare attributes for the object under the given interface.
     * The `vtable' parameter may contain method declarations (using MethodVTableItem struct),
     * property declarations (using PropertyVTableItem struct), signal declarations (using
     * SignalVTableItem struct), or global interface flags (using InterfaceFlagsVTableItem struct).
     *
     * An interface can have any number of vtables attached to it.
     *
     * Consult manual pages for the underlying `sd_bus_add_object_vtable` function for more information.
     *
     * The method can be called at any time during object's lifetime. For each vtable an internal
     * registration resource is created and is returned to the caller. The returned resource should
     * be destroyed when the vtable is not needed anymore.
     *
     * The function provides strong exception guarantee. The state of the object remains
     * unmodified in face of an exception.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): Resource

    /**
     * Creates a signal message
     *
     * @param interfaceName Name of an interface that the signal belongs under
     * @param signalName Name of the signal
     * @return A signal message
     *
     * Serialize signal arguments into the returned message and emit the signal by passing
     * the message with serialized arguments to the @c emitSignal function.
     * Alternatively, use higher-level API @c emitSignal(signalName: String) defined below.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal

    /**
     * Emits signal for this object path
     *
     * @param message Signal message to be sent out
     *
     * Note: To avoid messing with messages, use higher-level API defined below.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun emitSignal(message: Signal)
}

/**
 * Creates instance representing a D-Bus object
 *
 * @param connection D-Bus connection to be used by the object
 * @param objectPath Path of the D-Bus object
 * @return [Object] representation instance
 *
 * The provided connection will be used by the object to export methods,
 * issue signals and provide properties.
 *
 * Creating a D-Bus object instance is (thread-)safe even upon the connection
 * which is already running its I/O event loop.
 *
 * Code example:
 * ```
 * val proxy = createObject(connection, ObjectPath("/com/kistler/foo"))
 * ```
 */
expect fun createObject(connection: Connection, objectPath: ObjectPath): Object
