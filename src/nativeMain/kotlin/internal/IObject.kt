package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.header.Flags
import com.monkopedia.sdbus.header.SignalEmitter

///********************************************//**
// * @class IObject
// *
// * IObject class represents a D-Bus object instance identified by a specific object path.
// * D-Bus object provides its interfaces, methods, signals and properties on a bus
// * identified by a specific bus name.
// *
// * All IObject member methods throw @c sdbus::Error in case of D-Bus or sdbus-c++ error.
// * The IObject class has been designed as thread-aware. However, the operation of
// * creating and sending asynchronous method replies, as well as creating and emitting
// * signals, is thread-safe by design.
// *
// ***********************************************/
//interface IObject {
//
//
//        /*!
//         * @brief Registers method that the object will provide on D-Bus
//         *
//         * @param[in] interfaceName Name of an interface that the method will belong to
//         * @param[in] methodName Name of the method
//         * @param[in] inputSignature D-Bus signature of method input parameters
//         * @param[in] outputSignature D-Bus signature of method output parameters
//         * @param[in] methodCallback Callback that implements the body of the method
//         * @param[in] flags D-Bus method flags (privileged, deprecated, or no reply)
//         *
//         * @throws sdbus::Error in case of failure
//         */
//    fun registerMethod(interfaceName: String, methodName: String, inputSignature: String, outputSignature: String, methodCallback: method_callback, flags: Flags)
//
//
//    /*!
//     * @brief Registers method that the object will provide on D-Bus
//     *
//     * @param[in] interfaceName Name of an interface that the method will belong to
//     * @param[in] methodName Name of the method
//     * @param[in] inputSignature D-Bus signature of method input parameters
//     * @param[in] inputNames Names of input parameters
//     * @param[in] outputSignature D-Bus signature of method output parameters
//     * @param[in] outputNames Names of output parameters
//     * @param[in] methodCallback Callback that implements the body of the method
//     * @param[in] flags D-Bus method flags (privileged, deprecated, or no reply)
//     *
//     * Provided names of input and output parameters will be included in the introspection
//     * description (given that at least version 242 of underlying libsystemd library is
//     * used; otherwise, names of parameters are ignored). This usually helps better describe
//     * the API to the introspector.
//     *
//     * @throws sdbus::Error in case of failure
//     */
//    fun registerMethod(interfaceName: String, methodName: String, inputSignature: String, inputNames: List<String>, outputSignature: String, outputNames: List<String>, methodCallback: method_callback, flags: Flags)
//
//
//
//
//    /*!
//     * @brief Registers signal that the object will emit on D-Bus
//     *
//     * @param[in] interfaceName Name of an interface that the signal will fall under
//     * @param[in] signalName Name of the signal
//     * @param[in] signature D-Bus signature of signal parameters
//     * @param[in] flags D-Bus signal flags (deprecated)
//     *
//     * @throws sdbus::Error in case of failure
//     */
//    fun registerSignal(interfaceName: String, signalName: String, signature: String, flags: Flags)
//
//    /*!
//     * @brief Registers signal that the object will emit on D-Bus
//     *
//     * @param[in] interfaceName Name of an interface that the signal will fall under
//     * @param[in] signalName Name of the signal
//     * @param[in] signature D-Bus signature of signal parameters
//     * @param[in] paramNames Names of parameters of the signal
//     * @param[in] flags D-Bus signal flags (deprecated)
//     *
//     * Provided names of signal output parameters will be included in the introspection
//     * description (given that at least version 242 of underlying libsystemd library is
//     * used; otherwise, names of parameters are ignored). This usually helps better describe
//     * the API to the introspector.
//     *
//     * @throws sdbus::Error in case of failure
//     */
//    fun registerSignal(interfaceName: String, signalName: String, signature: String, paramNames: List<String>, flags: Flags)
//
//
//        /*!
//         * @brief Registers read-only property that the object will provide on D-Bus
//         *
//         * @param[in] interfaceName Name of an interface that the property will fall under
//         * @param[in] propertyName Name of the property
//         * @param[in] signature D-Bus signature of property parameters
//         * @param[in] getCallback Callback that implements the body of the property getter
//         * @param[in] flags D-Bus property flags (deprecated, property update behavior)
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun registerProperty(interfaceName: String, propertyName: String, signature: String, getCallback: property_get_callback, flags: Flags)
//
//        /*!
//         * @brief Registers read/write property that the object will provide on D-Bus
//         *
//         * @param[in] interfaceName Name of an interface that the property will fall under
//         * @param[in] propertyName Name of the property
//         * @param[in] signature D-Bus signature of property parameters
//         * @param[in] getCallback Callback that implements the body of the property getter
//         * @param[in] setCallback Callback that implements the body of the property setter
//         * @param[in] flags D-Bus property flags (deprecated, property update behavior)
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun registerProperty(interfaceName: String, propertyName: String, signature: String, getCallback: property_get_callback, setCallback property_set_callback, flags: Flags)
//
//        /*!
//         * @brief Sets flags for a given interface
//         *
//         * @param[in] interfaceName Name of an interface whose flags will be set
//         * @param[in] flags Flags to be set
//         *
//         * @throws sdbus::Error in case of failure
//         */
//
//    fun setInterfaceFlags(interfaceName: String, flags: Flags)
//
//        /*!
//         * @brief Finishes object API registration and publishes the object on the bus
//         *
//         * The method exports all up to now registered methods, signals and properties on D-Bus.
//         * Must be called after all methods, signals and properties have been registered.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//
//    fun finishRegistration()
//
//        /*!
//         * @brief Unregisters object's API and removes object from the bus
//         *
//         * This method unregisters the object, its interfaces, methods, signals and properties
//         * from the bus. Unregistration is done automatically also in object's destructor. This
//         * method makes sense if, in the process of object removal, we need to make sure that
//         * callbacks are unregistered explicitly before the final destruction of the object instance.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun unregister()
//
//
//        /*!
//         * @brief Creates a signal message
//         *
//         * @param[in] interfaceName Name of an interface that the signal belongs under
//         * @param[in] signalName Name of the signal
//         * @return A signal message
//         *
//         * Serialize signal arguments into the returned message and emit the signal by passing
//         * the message with serialized arguments to the @c emitSignal function.
//         * Alternatively, use higher-level API @c emitSignal(const std::string& signalName) defined below.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun createSignal(interfaceName: String, signalName: String): Signal
//
//
//        /*!
//         * @brief Emits signal for this object path
//         *
//         * @param[in] message Signal message to be sent out
//         *
//         * Note: To avoid messing with messages, use higher-level API defined below.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitSignal(message: Signal)
//
//
//        /*!
//         * @brief Emits PropertyChanged signal for specified properties under a given interface of this object path
//         *
//         * @param[in] interfaceName Name of an interface that properties belong to
//         * @param[in] propNames Names of properties that will be included in the PropertiesChanged signal
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitPropertiesChangedSignal(interfaceName: String, propNames: List<String>)
//
//
//        /*!
//         * @brief Emits PropertyChanged signal for all properties on a given interface of this object path
//         *
//         * @param[in] interfaceName Name of an interface
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitPropertiesChangedSignal(interfaceName: String)
//
//
//        /*!
//         * @brief Emits InterfacesAdded signal on this object path
//         *
//         * This emits an InterfacesAdded signal on this object path, by iterating all registered
//         * interfaces on the path. All properties are queried and included in the signal.
//         * This call is equivalent to emitInterfacesAddedSignal() with an explicit list of
//         * registered interfaces. However, unlike emitInterfacesAddedSignal(interfaces), this
//         * call can figure out the list of supported interfaces itself. Furthermore, it properly
//         * adds the builtin org.freedesktop.DBus.* interfaces.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitInterfacesAddedSignal()
//
//
//        /*!
//         * @brief Emits InterfacesAdded signal on this object path
//         *
//         * This emits an InterfacesAdded signal on this object path with explicitly provided list
//         * of registered interfaces. As sdbus-c++ does currently not supported adding/removing
//         * interfaces of an existing object at run time (an object has a fixed set of interfaces
//         * registered by the time of invoking finishRegistration()), emitInterfacesAddedSignal(void)
//         * is probably what you are looking for.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitInterfacesAddedSignal(interfaces: List<String>)
//
//
//        /*!
//         * @brief Emits InterfacesRemoved signal on this object path
//         *
//         * This is like sd_bus_emit_object_added(), but emits an InterfacesRemoved signal on this
//         * object path. This only includes any registered interfaces but skips the properties.
//         * This function shall be called (just) before destroying the object.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitInterfacesRemovedSignal()
//
//        /*!
//         * @brief Emits InterfacesRemoved signal on this object path
//         *
//         * This emits an InterfacesRemoved signal on the given path with explicitly provided list
//         * of registered interfaces. As sdbus-c++ does currently not supported adding/removing
//         * interfaces of an existing object at run time (an object has a fixed set of interfaces
//         * registered by the time of invoking finishRegistration()), emitInterfacesRemovedSignal(void)
//         * is probably what you are looking for.
//         *
//         * @throws sdbus::Error in case of failure
//         */
//        fun emitInterfacesRemovedSignal(interfaces: List<String>)
//
//
//
//
//    /*!
// * @brief Adds an ObjectManager interface at the path of this D-Bus object
// *
// * Creates an ObjectManager interface at the specified object path on
// * the connection. This is a convenient way to interrogate a connection
// * to see what objects it has.
// *
// * @throws sdbus::Error in case of failure
// */
//    fun addObjectManager()
//
//    /*!
//     * @brief Removes an ObjectManager interface from the path of this D-Bus object
//     *
//     * @throws sdbus::Error in case of failure
//     */
//    fun removeObjectManager()
//
//    /*!
//     * @brief Tests whether ObjectManager interface is added at the path of this D-Bus object
//     * @return True if ObjectManager interface is there, false otherwise
//     */
//    fun hasObjectManager() :Boolean
//
//    /*!
//     * @brief Provides D-Bus connection used by the object
//     *
//     * @return Reference to the D-Bus connection
//     */
//    fun  getConnection() : IConnection
//
//
//
//    /*!
//     * @brief Returns object path of the underlying DBus object
//     */
//    fun getObjectPath() : String
//
//    /*!
//     * @brief Provides currently processed D-Bus message
//     *
//     * This method provides immutable access to the currently processed incoming D-Bus message.
//     * "Currently processed" means that the registered callback handler(s) for that message
//     * are being invoked. This method is meant to be called from within a callback handler
//     * (e.g. D-Bus method implementation handler). In such a case it is guaranteed to return
//     * a valid pointer to the D-Bus message for which the handler is called. If called from other
//     * contexts/threads, it may return a nonzero pointer or a nullptr, depending on whether a message
//     * was processed at the time of call or not, but the value is nondereferencable, since the pointed-to
//     * message may have gone in the meantime.
//     *
//     * @return A pointer to the currently processed D-Bus message
//     */
//    fun getCurrentlyProcessedMessage(): Message;
//
//    companion object {
//
//        /*!
//         * @brief Creates instance representing a D-Bus object
//         *
//         * @param[in] connection D-Bus connection to be used by the object
//         * @param[in] objectPath Path of the D-Bus object
//         * @return Pointer to the object representation instance
//         *
//         * The provided connection will be used by the object to export methods,
//         * issue signals and provide properties.
//         *
//         * Creating a D-Bus object instance is (thread-)safe even upon the connection
//         * which is already running its I/O event loop.
//         *
//         * Code example:
//         * @code
//         * auto proxy = sdbus::createObject(connection, "/com/kistler/foo");
//         * @endcode
//         */
//        fun createObject(connection: IConnection, objectPath: String): IObject
//    }
//};
//
//// Out-of-line member definitions
//
///*!
// * @brief Registers method that the object will provide on D-Bus
// *
// * @param[in] methodName Name of the method
// * @return A helper object for convenient registration of the method
// *
// * This is a high-level, convenience way of registering D-Bus methods that abstracts
// * from the D-Bus message concept. Method arguments/return value are automatically (de)serialized
// * in a message and D-Bus signatures automatically deduced from the parameters and return type
// * of the provided native method implementation callback.
// *
// * Example of use:
// * @code
// * object.registerMethod("doFoo").onInterface("com.kistler.foo").implementedAs([this](int value){ return this->doFoo(value); });
// * @endcode
// *
// * @throws sdbus::Error in case of failure
// */
//inline fun IObject.registerMethod(methodName: String): MethodRegistrator {
//    return MethodRegistrator(this, methodName);
//}
///*!
//     * @brief Registers signal that the object will provide on D-Bus
//     *
//     * @param[in] signalName Name of the signal
//     * @return A helper object for convenient registration of the signal
//     *
//     * This is a high-level, convenience way of registering D-Bus signals that abstracts
//     * from the D-Bus message concept. Signal arguments are automatically (de)serialized
//     * in a message and D-Bus signatures automatically deduced from the provided native parameters.
//     *
//     * Example of use:
//     * @code
//     * object.registerSignal("paramChange").onInterface("com.kistler.foo").withParameters<std::map<int32_t, std::string>>();
//     * @endcode
//     *
//     * @throws sdbus::Error in case of failure
//     */
//
//inline fun IObject.registerSignal(signalName: String): SignalRegistrator {
//    return SignalRegistrator(this, signalName);
//}
//
///*!
// * @brief Registers property that the object will provide on D-Bus
// *
// * @param[in] propertyName Name of the property
// * @return A helper object for convenient registration of the property
// *
// * This is a high-level, convenience way of registering D-Bus properties that abstracts
// * from the D-Bus message concept. Property arguments are automatically (de)serialized
// * in a message and D-Bus signatures automatically deduced from the provided native callbacks.
// *
// * Example of use:
// * @code
// * object_.registerProperty("state").onInterface("com.kistler.foo").withGetter([this](){ return this->state(); });
// * @endcode
// *
// * @throws sdbus::Error in case of failure
// */
//
//inline fun IObject.registerProperty(propertyName: String): PropertyRegistrator {
//    return PropertyRegistrator(this, propertyName: String);
//}
//
///*!
// * @brief Sets flags (annotations) for a given interface
// *
// * @param[in] interfaceName Name of an interface whose flags will be set
// * @return A helper object for convenient setting of Interface flags
// *
// * This is a high-level, convenience alternative to the other setInterfaceFlags overload.
// *
// * Example of use:
// * @code
// * object_.setInterfaceFlags("com.kistler.foo").markAsDeprecated().withPropertyUpdateBehavior(sdbus::Flags::EMITS_NO_SIGNAL);
// * @endcode
// *
// * @throws sdbus::Error in case of failure
// */
//inline fun IObject.setInterfaceFlags(interfaceName: String): InterfaceFlagsSetter {
//    return InterfaceFlagsSetter(this, interfaceName);
//}
//
///*!
// * @brief Emits signal on D-Bus
// *
// * @param[in] signalName Name of the signal
// * @return A helper object for convenient emission of signals
// *
// * This is a high-level, convenience way of emitting D-Bus signals that abstracts
// * from the D-Bus message concept. Signal arguments are automatically serialized
// * in a message and D-Bus signatures automatically deduced from the provided native arguments.
// *
// * Example of use:
// * @code
// * int arg1 = ...;
// * double arg2 = ...;
// * object_.emitSignal("fooSignal").onInterface("com.kistler.foo").withArguments(arg1, arg2);
// * @endcode
// *
// * @throws sdbus::Error in case of failure
// */
//inline IObject.emitSignal(signalName): SignalEmitter {
//    return SignalEmitter(this, signalName: String);
//}
//
