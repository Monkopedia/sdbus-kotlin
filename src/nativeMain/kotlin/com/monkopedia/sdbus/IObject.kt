@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.Object
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EINVAL

/********************************************/
/**
 * @class IObject
 *
 * IObject class represents a D-Bus object instance identified by a specific object path.
 * D-Bus object provides its interfaces, methods, signals and properties on a bus
 * identified by a specific bus name.
 *
 * All IObject member methods throw @c sdbus::Error in case of D-Bus or sdbus-c++ error.
 * The IObject class has been designed as thread-aware. However, the operation of
 * creating and sending asynchronous method replies, as well as creating and emitting
 * signals, is thread-safe by design.
 *
 ***********************************************/
interface IObject : Resource {


    /*!
     * @brief Emits PropertyChanged signal for specified properties under a given interface of this object path
     *
     * @param[in] interfaceName Name of an interface that properties belong to
     * @param[in] propNames Names of properties that will be included in the PropertiesChanged signal
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitPropertiesChangedSignal(interfaceName: InterfaceName, propNames: List<PropertyName>)

    /*!
     * @copydoc IObject::emitPropertiesChangedSignal(const InterfaceName&,const std::vector<PropertyName>&)
     */
    fun emitPropertiesChangedSignal(interfaceName: String, propNames: List<PropertyName>)

    /*!
     * @brief Emits PropertyChanged signal for all properties on a given interface of this object pat
     *
     * @param[in] interfaceName Name of an interface
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitPropertiesChangedSignal(interfaceName: InterfaceName)

    /*!
     * @copydoc IObject::emitPropertiesChangedSignal(const InterfaceName&)
     */
    fun emitPropertiesChangedSignal(interfaceName: String)

    /*!
     * @brief Emits InterfacesAdded signal on this object path
     *
     * This emits an InterfacesAdded signal on this object path, by iterating all registered
     * interfaces on the path. All properties are queried and included in the signal.
     * This call is equivalent to emitInterfacesAddedSignal() with an explicit list of
     * registered interfaces. However, unlike emitInterfacesAddedSignal(interfaces), this
     * call can figure out the list of supported interfaces itself. Furthermore, it properly
     * adds the builtin org.freedesktop.DBus.* interfaces.
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitInterfacesAddedSignal()

    /*!
     * @brief Emits InterfacesAdded signal on this object path
     *
     * This emits an InterfacesAdded signal on this object path with explicitly provided list
     * of registered interfaces. Since v2.0, sdbus-c++ supports dynamically addable/removable
     * object interfaces and their vtables, so this method now makes more sense.
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>)

    /*!
     * @brief Emits InterfacesRemoved signal on this object path
     *
     * This is like sd_bus_emit_object_added(), but emits an InterfacesRemoved signal on this
     * object path. This only includes any registered interfaces but skips the properties.
     * This function shall be called (just) before destroying the object.
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitInterfacesRemovedSignal()

    /*!
     * @brief Emits InterfacesRemoved signal on this object path
     *
     * This emits an InterfacesRemoved signal on the given path with explicitly provided list
     * of registered interfaces. Since v2.0, sdbus-c++ supports dynamically addable/removable
     * object interfaces and their vtables, so this method now makes more sense.
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>)

    /*!
     * @brief Adds an ObjectManager interface at the path of this D-Bus object
     *
     * Creates an ObjectManager interface at the specified object path on
     * the connection. This is a convenient way to interrogate a connection
     * to see what objects it has.
     *
     * This call creates a so-called floating registration. This means that
     * the ObjectManager interface stays there for the lifetime of the object.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addObjectManager()

    /*!
     * @brief Adds an ObjectManager interface at the path of this D-Bus object
     *
     * @return Slot handle owning the registration
     *
     * Creates an ObjectManager interface at the specified object path on
     * the connection. This is a convenient way to interrogate a connection
     * to see what objects it has.
     *
     * The lifetime of the ObjectManager interface is bound to the lifetime
     * of the returned slot instance.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addObjectManager(t: return_slot_t): Resource

    /*!
     * @brief Provides D-Bus connection used by the object
     *
     * @return Reference to the D-Bus connection
     */
    fun getConnection(): com.monkopedia.sdbus.IConnection

    /*!
     * @brief Returns object path of the underlying DBus object
     */
    fun getObjectPath(): ObjectPath

    /*!
     * @brief Provides access to the currently processed D-Bus message
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
    fun getCurrentlyProcessedMessage(): Message

    /*!
     * @brief Adds a declaration of methods, properties and signals of the object at a given interface
     *
     * @param[in] interfaceName Name of an interface the the vtable is registered for
     * @param[in] vtable A list of individual descriptions in the form of VTable item instances
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
     * registration slot is created and its lifetime is tied to the lifetime of the Object instance.
     *
     * The function provides strong exception guarantee. The state of the object remains
     * unmodified in face of an exception.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>)

    /*!
     * @brief Adds a declaration of methods, properties and signals of the object at a given interface
     *
     * @param[in] interfaceName Name of an interface the the vtable is registered for
     * @param[in] vtable A list of individual descriptions in the form of VTable item instances
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
     * registration slot is created and is returned to the caller. The returned slot should be destroyed
     * when the vtable is not needed anymore. This allows for "dynamic" object API where vtables
     * can be added or removed by the user at runtime.
     *
     * The function provides strong exception guarantee. The state of the object remains
     * unmodified in face of an exception.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addVTable(
        interfaceName: InterfaceName,
        vtable: List<VTableItem>,
        return_slot: return_slot_t
    ): Resource

    /*!
     * @brief Creates a signal message
     *
     * @param[in] interfaceName Name of an interface that the signal belongs under
     * @param[in] signalName Name of the signal
     * @return A signal message
     *
     * Serialize signal arguments into the returned message and emit the signal by passing
     * the message with serialized arguments to the @c emitSignal function.
     * Alternatively, use higher-level API @c emitSignal(signalName: String) defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal

    /*!
     * @brief Emits signal for this object path
     *
     * @param[in] message Signal message to be sent out
     *
     * Note: To avoid messing with messages, use higher-level API defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun emitSignal(message: Signal)

    fun createSignal(interfaceName: String, signalName: String): Signal
};

// Out-of-line member definitions

/*!
 * @brief Emits signal on D-Bus
 *
 * @param[in] signalName Name of the signal
 * @return A helper object for convenient emission of signals
 *
 * This is a high-level, convenience way of emitting D-Bus signals that abstracts
 * from the D-Bus message concept. Signal arguments are automatically serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments.
 *
 * Example of use:
 * @code
 * int arg1 = ...;
 * double arg2 = ...;
 * SignalName fooSignal{"fooSignal"};
 * object_.emitSignal(fooSignal).onInterface("com.kistler.foo").withArguments(arg1, arg2);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IObject.emitSignal(signalName: SignalName): SignalEmitter {
    return SignalEmitter(this@emitSignal, signalName);
}

/*!
 * @copydoc IObject::emitSignal(const SignalName&)
 */
inline fun IObject.emitSignal(signalName: String): SignalEmitter {
    return SignalEmitter(this@emitSignal, signalName);
}

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
inline fun IObject.addVTable(vararg vtable: VTableItem): VTableAdder {
    return VTableAdder(this, vtable.toList());
}

/*!
 * @brief Creates instance representing a D-Bus object
 *
 * @param[in] connection D-Bus connection to be used by the object
 * @param[in] objectPath Path of the D-Bus object
 * @return Pointer to the object representation instance
 *
 * The provided connection will be used by the object to export methods,
 * issue signals and provide properties.
 *
 * Creating a D-Bus object instance is (thread-)safe even upon the connection
 * which is already running its I/O event loop.
 *
 * Code example:
 * @code
 * auto proxy = sdbus::createObject(connection, "/com/kistler/foo");
 * @endcode
 */
fun createObject(connection: com.monkopedia.sdbus.IConnection, objectPath: ObjectPath): IObject {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.IConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-c++ connection",
        EINVAL
    );

    return Object(sdbusConnection!!, objectPath);
}
