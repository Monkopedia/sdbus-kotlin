@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.Proxy
import com.monkopedia.sdbus.internal.Proxy.AsyncCallInfo
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.time.Duration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import platform.posix.EINVAL

/********************************************/
/**
 * @class IProxy
 *
 * IProxy class represents a proxy object, which is a convenient local object created
 * to represent a remote D-Bus object in another process.
 * The proxy enables calling methods on remote objects, receiving signals from remote
 * objects, and getting/setting properties of remote objects.
 *
 * All IProxy member methods throw @c sdbus::Error in case of D-Bus or sdbus-c++ error.
 * The IProxy class has been designed as thread-aware. However, the operation of
 * creating and sending method calls (both synchronously and asynchronously) is
 * thread-safe by design.
 *
 ***********************************************/
interface IProxy : Resource {

    /*!
     * @brief Provides D-Bus connection used by the proxy
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
     * @brief Creates a method call message
     *
     * @param[in] interfaceName Name of an interface that provides a given method
     * @param[in] methodName Name of the method
     * @return A method call message
     *
     * Serialize method arguments into the returned message and invoke the method by passing
     * the message with serialized arguments to the @c callMethod function.
     * Alternatively, use higher-level API @c callMethod(const & methodName: String) defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun createMethodCall(interfaceName: InterfaceName, methodName: MethodName): MethodCall

    /*!
     * @brief Calls method on the remote D-Bus object
     *
     * @param[in] message Message representing a method call
     * @return A method reply message
     *
     * The call does not block if the method call has dont-expect-reply flag set. In that case,
     * the call returns immediately and the return value is an empty, invalid method reply.
     *
     * The call blocks otherwise, waiting for the remote peer to send back a reply or an error,
     * or until the call times out.
     *
     * While blocking, other concurrent operations (in other threads) on the underlying bus
     * connection are stalled until the call returns. This is not an issue in vast majority of
     * (simple, single-threaded) applications. In asynchronous, multi-threaded designs involving
     * shared bus connections, this may be an issue. It is advised to instead use an asynchronous
     * callMethod() function overload, which does not block the bus connection, or do the synchronous
     * call from another Proxy instance created just before the call and then destroyed (which is
     * anyway quite a typical approach in D-Bus implementations). Such proxy instance must have
     * its own bus connection. So-called light-weight proxies (ones created with `dont_run_event_loop_thread`
     * tag are designed for exactly that purpose.
     *
     * The default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use API on a higher level of abstraction defined below.
     *
     * @throws sdbus::Error in case of failure (also in case the remote function returned an error)
     */
    fun callMethod(message: MethodCall): MethodReply

    /*!
     * @brief Calls method on the remote D-Bus object
     *
     * @param[in] message Message representing a method call
     * @param[in] timeout Method call timeout (in microseconds)
     * @return A method reply message
     *
     * The call does not block if the method call has dont-expect-reply flag set. In that case,
     * the call returns immediately and the return value is an empty, invalid method reply.
     *
     * The call blocks otherwise, waiting for the remote peer to send back a reply or an error,
     * or until the call times out.
     *
     * While blocking, other concurrent operations (in other threads) on the underlying bus
     * connection are stalled until the call returns. This is not an issue in vast majority of
     * (simple, single-threaded) applications. In asynchronous, multi-threaded designs involving
     * shared bus connections, this may be an issue. It is advised to instead use an asynchronous
     * callMethod() function overload, which does not block the bus connection, or do the synchronous
     * call from another Proxy instance created just before the call and then destroyed (which is
     * anyway quite a typical approach in D-Bus implementations). Such proxy instance must have
     * its own bus connection. So-called light-weight proxies (ones created with `dont_run_event_loop_thread`
     * tag are designed for exactly that purpose.
     *
     * If timeout is zero, the default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use API on a higher level of abstraction defined below.
     *
     * @throws sdbus::Error in case of failure (also in case the remote function returned an error)
     */
    fun callMethod(message: MethodCall, timeout: ULong): MethodReply

    /*!
     * @brief Calls method on the D-Bus object asynchronously
     *
     * @param[in] message Message representing an async method call
     * @param[in] asyncReplyCallback Handler for the async reply
     * @return Observing handle for the the pending asynchronous call
     *
     * This is a callback-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided async reply handler will get invoked from the context of the bus
     * connection I/O event loop thread.
     *
     * An non-owning, observing async call handle is returned that can be used to query call status or cancel the call.
     *
     * The default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use API on a higher level of abstraction defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): PendingAsyncCall

    /*!
     * @brief Calls method on the D-Bus object asynchronously
     *
     * @param[in] message Message representing an async method call
     * @param[in] asyncReplyCallback Handler for the async reply
     * @return RAII-style slot handle representing the ownership of the async call
     *
     * This is a callback-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided async reply handler will get invoked from the context of the bus
     * connection I/O event loop thread.
     *
     * A slot (an owning handle) is returned for the async call. Lifetime of the call is bound to the lifetime of the slot.
     * The slot can be used to cancel the method call at a later time by simply destroying it.
     *
     * The default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use API on a higher level of abstraction defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        return_slot: return_slot_t
    ): Resource

    /*!
     * @brief Calls method on the D-Bus object asynchronously, with custom timeout
     *
     * @param[in] message Message representing an async method call
     * @param[in] asyncReplyCallback Handler for the async reply
     * @param[in] timeout Method call timeout (in microseconds)
     * @return Observing handle for the the pending asynchronous call
     *
     * This is a callback-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided async reply handler will get invoked from the context of the bus
     * connection I/O event loop thread.
     *
     * An non-owning, observing async call handle is returned that can be used to query call status or cancel the call.
     *
     * If timeout is zero, the default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use API on a higher level of abstraction defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall

    /*!
     * @brief Calls method on the D-Bus object asynchronously, with custom timeout
     *
     * @param[in] message Message representing an async method call
     * @param[in] asyncReplyCallback Handler for the async reply
     * @param[in] timeout Method call timeout (in microseconds)
     * @return RAII-style slot handle representing the ownership of the async call
     *
     * This is a callback-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided async reply handler will get invoked from the context of the bus
     * connection I/O event loop thread.
     *
     * A slot (an owning handle) is returned for the async call. Lifetime of the call is bound to the lifetime of the slot.
     * The slot can be used to cancel the method call at a later time by simply destroying it.
     *
     * If timeout is zero, the default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use API on a higher level of abstraction defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong,
        t: return_slot_t
    ): Resource

    /*!
     * @brief Calls method on the D-Bus object asynchronously
     *
     * @param[in] message Message representing an async method call
     * @param[in] Tag denoting a std::future-based overload
     * @return Future object providing access to the future method reply message
     *
     * This is a std::future-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided future object will be set to contain the reply (or sdbus::Error
     * in case the remote method threw an exception).
     *
     * The default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use higher-level API defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    suspend fun callMethodAsync(message: MethodCall, with_future: with_future_t): MethodReply

    /*!
     * @brief Calls method on the D-Bus object asynchronously, with custom timeout
     *
     * @param[in] message Message representing an async method call
     * @param[in] timeout Method call timeout
     * @param[in] Tag denoting a std::future-based overload
     * @return Future object providing access to the future method reply message
     *
     * This is a std::future-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided future object will be set to contain the reply (or sdbus::Error
     * in case the remote method threw an exception, or the call timed out).
     *
     * If timeout is zero, the default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use higher-level API defined below.
     *
     * @throws sdbus::Error in case of failure
     */
    suspend fun callMethodAsync(message: MethodCall, timeout: ULong, t: with_future_t): MethodReply

    /*!
     * @brief Registers a handler for the desired signal emitted by the D-Bus object
     *
     * @param[in] interfaceName Name of an interface that the signal belongs to
     * @param[in] signalName Name of the signal
     * @param[in] signalHandler Callback that implements the body of the signal handler
     *
     * A signal can be subscribed to at any time during proxy lifetime. The subscription
     * is active immediately after the call, and stays active for the entire lifetime
     * of the Proxy object.
     *
     * To be able to unsubscribe from the signal at a later time, use the registerSignalHandler()
     * overload with request_slot tag.
     *
     * @throws sdbus::Error in case of failure
     */
    fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    )

    /*!
     * @brief Registers a handler for the desired signal emitted by the D-Bus object
     *
     * @param[in] interfaceName Name of an interface that the signal belongs to
     * @param[in] signalName Name of the signal
     * @param[in] signalHandler Callback that implements the body of the signal handler
     *
     * @return RAII-style slot handle representing the ownership of the subscription
     *
     * A signal can be subscribed to and unsubscribed from at any time during proxy
     * lifetime. The subscription is active immediately after the call. The lifetime
     * of the subscription is bound to the lifetime of the slot object. The subscription
     * is unregistered by letting go of the slot object.
     *
     * @throws sdbus::Error in case of failure
     */
    fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler,
        return_slot: return_slot_t
    ): Resource

    fun createMethodCall(interfaceName: String, methodName: String): MethodCall

    fun registerSignalHandler(
        interfaceName: String,
        signalName: String,
        signalHandler: SignalHandler
    )

    fun registerSignalHandler(
        interfaceName: String,
        signalName: String,
        signalHandler: SignalHandler,
        return_slot: return_slot_t
    ): Resource
}

/********************************************/
/**
 * @class PendingAsyncCall
 *
 * PendingAsyncCall represents a simple handle type to cancel the delivery
 * of the asynchronous D-Bus call result to the application.
 *
 * The handle is lifetime-independent from the originating Proxy object.
 * It's safe to call its methods even after the Proxy has gone.
 *
 ***********************************************/
class PendingAsyncCall internal constructor(private val target: WeakReference<AsyncCallInfo>) {

    /*!
     * @brief Cancels the delivery of the pending asynchronous call result
     *
     * This function effectively removes the callback handler registered to the
     * async D-Bus method call result delivery. Does nothing if the call was
     * completed already, or if the originating Proxy object has gone meanwhile.
     */
    fun cancel() {
        val asyncCallInfo = target.get() ?: return
        asyncCallInfo.proxy.erase(asyncCallInfo)
    }

    /*!
     * @brief Answers whether the asynchronous call is still pending
     *
     * @return True if the call is pending, false if the call has been fully completed
     *
     * Pending call in this context means a call whose results have not arrived, or
     * have arrived and are currently being processed by the callback handler.
     */
    fun isPending(): Boolean = target.get()?.finished == false
}

// Out-of-line member definitions

inline fun IProxy.callMethod(message: MethodCall, timeout: Duration): MethodReply =
    callMethod(message, timeout.inWholeMicroseconds.toULong())

inline fun IProxy.callMethodAsync(
    message: MethodCall,
    noinline asyncReplyCallback: AsyncReplyHandler,
    timeout: Duration
): PendingAsyncCall =
    callMethodAsync(message, asyncReplyCallback, timeout.inWholeMicroseconds.toULong())

inline fun IProxy.callMethodAsync(
    message: MethodCall,
    noinline asyncReplyCallback: AsyncReplyHandler,
    timeout: Duration,
    return_slot: return_slot_t
): Resource = callMethodAsync(
    message,
    asyncReplyCallback,
    timeout.inWholeMicroseconds.toULong(),
    return_slot
)

suspend inline fun IProxy.callMethodAsync(
    message: MethodCall,
    timeout: Duration,
    with_future: with_future_t
): MethodReply = callMethodAsync(message, timeout.inWholeMicroseconds.toULong(), with_future)

/*!
 * @brief Calls method on the D-Bus object
 *
 * @param[in] methodName Name of the method
 * @return A helper object for convenient invocation of the method
 *
 * This is a high-level, convenience way of calling D-Bus methods that abstracts
 * from the D-Bus message concept. Method arguments/return value are automatically (de)serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments
 * and return values.
 *
 * Example of use:
 * @code
 * int result, a = ..., b = ...;
 * MethodName multiply{"multiply"};
 * object_.callMethod(multiply).onInterface(INTERFACE_NAME).withArguments(a, b).storeResultsTo(result);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.callMethod(methodName: MethodName): MethodInvoker =
    MethodInvoker(this@callMethod, methodName)

/*!
 * @copydoc IProxy::callMethod(const MethodName&)
 */
inline fun IProxy.callMethod(methodName: String): MethodInvoker =
    MethodInvoker(this@callMethod, methodName)

/*!
 * @brief Calls method on the D-Bus object asynchronously
 *
 * @param[in] methodName Name of the method
 * @return A helper object for convenient asynchronous invocation of the method
 *
 * This is a high-level, convenience way of calling D-Bus methods that abstracts
 * from the D-Bus message concept. Method arguments/return value are automatically (de)serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments
 * and return values.
 *
 * Example of use:
 * @code
 * int a = ..., b = ...;
 * MethodName multiply{"multiply"};
 * object_.callMethodAsync(multiply).onInterface(INTERFACE_NAME).withArguments(a, b).uponReplyInvoke([](int result)
 * {
 *     std::cout << "Got result of multiplying " << a << " and " << b << ": " << result << std::endl;
 * });
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.callMethodAsync(methodName: MethodName): AsyncMethodInvoker =
    AsyncMethodInvoker(this@callMethodAsync, methodName)

/*!
 * @copydoc IProxy::callMethodAsync(const MethodName&)
 */
inline fun IProxy.callMethodAsync(methodName: String): AsyncMethodInvoker =
    AsyncMethodInvoker(this@callMethodAsync, methodName)

/*!
 * @brief Registers signal handler for a given signal of the D-Bus object
 *
 * @param[in] signalName Name of the signal
 * @return A helper object for convenient registration of the signal handler
 *
 * This is a high-level, convenience way of registering to D-Bus signals that abstracts
 * from the D-Bus message concept. Signal arguments are automatically serialized
 * in a message and D-Bus signatures automatically deduced from the parameters
 * of the provided native signal callback.
 *
 * A signal can be subscribed to and unsubscribed from at any time during proxy
 * lifetime. The subscription is active immediately after the call.
 *
 * Example of use:
 * @code
 * object_.uponSignal("stateChanged").onInterface("com.kistler.foo").call([this](int arg1, double arg2){ this->onStateChanged(arg1, arg2); });
 * sdbus::InterfaceName foo{"com.kistler.foo"};
 * sdbus::SignalName levelChanged{"levelChanged"};
 * object_.uponSignal(levelChanged).onInterface(foo).call([this](uint16_t level){ this->onLevelChanged(level); });
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.uponSignal(signalName: SignalName): SignalSubscriber =
    SignalSubscriber(this, signalName)

/*!
 * @copydoc IProxy::uponSignal(const SignalName&)
 */
inline fun IProxy.uponSignal(signalName: String): SignalSubscriber =
    SignalSubscriber(this, signalName)

/*!
 * @brief Gets value of a property of the D-Bus object
 *
 * @param[in] propertyName Name of the property
 * @return A helper object for convenient getting of property value
 *
 * This is a high-level, convenience way of reading D-Bus property values that abstracts
 * from the D-Bus message concept. sdbus::Variant is returned which shall then be converted
 * to the real property type (implicit conversion is supported).
 *
 * Example of use:
 * @code
 * int state = object.getProperty("state").onInterface("com.kistler.foo");
 * sdbus::InterfaceName foo{"com.kistler.foo"};
 * sdbus::PropertyName level{"level"};
 * int level = object.getProperty(level).onInterface(foo);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.getProperty(propertyName: PropertyName): PropertyGetter =
    PropertyGetter(this, propertyName.value)

/*!
 * @copydoc IProxy::getProperty(const PropertyName&)
 */
inline fun IProxy.getProperty(propertyName: String): PropertyGetter =
    PropertyGetter(this, propertyName)

/*!
 * @brief Gets value of a property of the D-Bus object asynchronously
 *
 * @param[in] propertyName Name of the property
 * @return A helper object for convenient asynchronous getting of property value
 *
 * This is a high-level, convenience way of reading D-Bus property values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * @code
 * std::future<sdbus::Variant> state = object.getPropertyAsync("state").onInterface("com.kistler.foo").getResultAsFuture();
 * auto callback = [](std::optional<sdbus::Error> err, const sdbus::Variant& value){ ... };
 * object.getPropertyAsync("state").onInterface("com.kistler.foo").uponReplyInvoke(std::move(callback));
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.getPropertyAsync(propertyName: PropertyName): AsyncPropertyGetter =
    AsyncPropertyGetter(this, propertyName.value)

/*!
 * @copydoc IProxy::getPropertyAsync(const PropertyName&)
 */
inline fun IProxy.getPropertyAsync(propertyName: String): AsyncPropertyGetter =
    AsyncPropertyGetter(this, propertyName)

/*!
 * @brief Sets value of a property of the D-Bus object
 *
 * @param[in] propertyName Name of the property
 * @return A helper object for convenient setting of property value
 *
 * This is a high-level, convenience way of writing D-Bus property values that abstracts
 * from the D-Bus message concept.
 * Setting property value with NoReply flag is also supported.
 *
 * Example of use:
 * @code
 * int state = ...;
 * object_.setProperty("state").onInterface("com.kistler.foo").toValue(state);
 * // Or we can just send the set message call without waiting for the reply
 * object_.setProperty("state").onInterface("com.kistler.foo").toValue(state, dont_expect_reply);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.setProperty(propertyName: PropertyName): PropertySetter =
    PropertySetter(this, propertyName.value)

/*!
 * @copydoc IProxy::setProperty(const PropertyName&)
 */
inline fun IProxy.setProperty(propertyName: String): PropertySetter =
    PropertySetter(this, propertyName)

/*!
 * @brief Sets value of a property of the D-Bus object asynchronously
 *
 * @param[in] propertyName Name of the property
 * @return A helper object for convenient asynchronous setting of property value
 *
 * This is a high-level, convenience way of writing D-Bus property values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * @code
 * int state = ...;
 * // We can wait until the set operation finishes by waiting on the future
 * std::future<void> res = object_.setPropertyAsync("state").onInterface("com.kistler.foo").toValue(state).getResultAsFuture();
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.setPropertyAsync(propertyName: PropertyName): AsyncPropertySetter =
    AsyncPropertySetter(this, propertyName.value)

/*!
 * @copydoc IProxy::setPropertyAsync(const PropertyName&)
 */
inline fun IProxy.setPropertyAsync(propertyName: String): AsyncPropertySetter =
    AsyncPropertySetter(this, propertyName)

/*!
 * @brief Gets values of all properties of the D-Bus object
 *
 * @return A helper object for convenient getting of properties' values
 *
 * This is a high-level, convenience way of reading D-Bus properties' values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * @code
 * auto props = object.getAllProperties().onInterface("com.kistler.foo");
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IProxy.getAllProperties(): AllPropertiesGetter = AllPropertiesGetter(this)

/*!
 * @brief Gets values of all properties of the D-Bus object asynchronously
 *
 * @return A helper object for convenient asynchronous getting of properties' values
 *
 * This is a high-level, convenience way of reading D-Bus properties' values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * @code
 * auto callback = [](std::optional<sdbus::Error> err, const std::map<PropertyName, Variant>>& properties){ ... };
 * auto props = object.getAllPropertiesAsync().onInterface("com.kistler.foo").uponReplyInvoke(std::move(callback));
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */

inline fun IProxy.getAllPropertiesAsync(): AsyncAllPropertiesGetter = AsyncAllPropertiesGetter(this)

/*!
 * @brief Creates a proxy object for a specific remote D-Bus object
 *
 * @param[in] connection D-Bus connection to be used by the proxy object
 * @param[in] destination Bus name that provides the remote D-Bus object
 * @param[in] objectPath Path of the remote D-Bus object
 * @return Pointer to the proxy object instance
 *
 * The provided connection will be used by the proxy to issue calls against the object,
 * and signals, if any, will be subscribed to on this connection. The caller still
 * remains the owner of the connection (the proxy just keeps a reference to it), and
 * should make sure that an I/O event loop is running on that connection, so the proxy
 * may receive incoming signals and asynchronous method replies.
 *
 * The destination parameter may be an empty string (useful e.g. in case of direct
 * D-Bus connections to a custom server bus).
 *
 * Code example:
 * @code
 * auto proxy = sdbus::createProxy(connection, "com.kistler.foo", "/com/kistler/foo");
 * @endcode
 */
fun createProxy(connection: com.monkopedia.sdbus.IConnection, destination: ServiceName, objectPath: ObjectPath): IProxy {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.IConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-c++ connection",
        EINVAL
    )

    return Proxy(sdbusConnection!!, destination, objectPath)
}

/*!
 * @brief Creates a proxy object for a specific remote D-Bus object
 *
 * @param[in] connection D-Bus connection to be used by the proxy object
 * @param[in] destination Bus name that provides the remote D-Bus object
 * @param[in] objectPath Path of the remote D-Bus object
 * @return Pointer to the object proxy instance
 *
 * The provided connection will be used by the proxy to issue calls against the object.
 * The Object proxy becomes an exclusive owner of this connection, but will not start
 * an event loop thread on this connection. This is cheap construction and is suitable
 * for short-lived proxies created just to execute simple synchronous D-Bus calls and
 * then destroyed. Such blocking request-reply calls will work without an event loop
 * (but signals, async calls, etc. won't).
 *
 * The destination parameter may be an empty string (useful e.g. in case of direct
 * D-Bus connections to a custom server bus).
 *
 * Code example:
 * @code
 * auto proxy = sdbus::createProxy(std::move(connection), "com.kistler.foo", "/com/kistler/foo", sdbus::dont_run_event_loop_thread);
 * @endcode
 */
fun createProxy(
    connection: com.monkopedia.sdbus.IConnection,
    destination: ServiceName,
    objectPath: ObjectPath,
    dont_run_event_loop_thread: dont_run_event_loop_thread_t
): IProxy {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.IConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-c++ connection",
        EINVAL
    )

    return Proxy(
        sdbusConnection!!,
        destination,
        objectPath,
        dont_run_event_loop_thread
    )
}

/*!
 * @brief Creates a proxy object for a specific remote D-Bus object
 *
 * @param[in] destination Bus name that provides the remote D-Bus object
 * @param[in] objectPath Path of the remote D-Bus object
 * @return Pointer to the object proxy instance
 *
 * No D-Bus connection is provided here, so the object proxy will create and manage
 * his own connection, and will automatically start an event loop upon that connection
 * in a separate internal thread. Handlers for incoming signals and asynchronous
 * method replies will be executed in the context of that thread.
 *
 * Code example:
 * @code
 * auto proxy = sdbus::createProxy("com.kistler.foo", "/com/kistler/foo");
 * @endcode
 */
fun createProxy(destination: ServiceName, objectPath: ObjectPath): IProxy = memScoped {
    val connection = com.monkopedia.sdbus.createBusConnection()

    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.IConnection
    assert(sdbusConnection != null)

    Proxy(sdbusConnection!!, destination, objectPath)
}

/*!
 * @brief Creates a proxy object for a specific remote D-Bus object
 *
 * @param[in] destination Bus name that provides the remote D-Bus object
 * @param[in] objectPath Path of the remote D-Bus object
 * @return Pointer to the object proxy instance
 *
 * No D-Bus connection is provided here, so the object proxy will create and manage
 * his own connection, but it will not start an event loop thread. This is cheap
 * construction and is suitable for short-lived proxies created just to execute simple
 * synchronous D-Bus calls and then destroyed. Such blocking request-reply calls
 * will work without an event loop (but signals, async calls, etc. won't).
 *
 * Code example:
 * @code
 * auto proxy = sdbus::createProxy("com.kistler.foo", "/com/kistler/foo", sdbus::dont_run_event_loop_thread );
 * @endcode
 */
fun createProxy(
    destination: ServiceName,
    objectPath: ObjectPath,
    dont_run_event_loop_thread: dont_run_event_loop_thread_t
): IProxy = memScoped {
    val connection = com.monkopedia.sdbus.createBusConnection()

    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.IConnection
    assert(sdbusConnection != null)

    Proxy(
        sdbusConnection!!,
        destination,
        objectPath,
        dont_run_event_loop_thread
    )
}
