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

import kotlin.time.Duration

/********************************************/
/**
 * @class IProxy
 *
 * IProxy class represents a proxy object, which is a convenient local object created
 * to represent a remote D-Bus object in another process.
 * The proxy enables calling methods on remote objects, receiving signals from remote
 * objects, and getting/setting properties of remote objects.
 *
 * All IProxy member methods throw @c [com.monkopedia.sdbus.Error] in case of D-Bus or sdbus-kotlin error.
 * The IProxy class has been designed as thread-aware. However, the operation of
 * creating and sending method calls (both synchronously and asynchronously) is
 * thread-safe by design.
 *
 ***********************************************/
interface Proxy : Resource {

    /**
     * Provides D-Bus connection used by the proxy
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
     * Creates a method call message
     *
     * @param interfaceName Name of an interface that provides a given method
     * @param methodName Name of the method
     * @return A method call message
     *
     * Serialize method arguments into the returned message and invoke the method by passing
     * the message with serialized arguments to the @c callMethod function.
     * Alternatively, use higher-level API @c callMethod(const & methodName: String) defined below.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun createMethodCall(interfaceName: InterfaceName, methodName: MethodName): MethodCall

    /**
     * Calls method on the remote D-Bus object
     *
     * @param message Message representing a method call
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
     * @throws [com.monkopedia.sdbus.Error] in case of failure (also in case the remote function returned an error)
     */
    fun callMethod(message: MethodCall): MethodReply

    /**
     * Calls method on the remote D-Bus object
     *
     * @param message Message representing a method call
     * @param timeout Method call timeout (in microseconds)
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
     * @throws [com.monkopedia.sdbus.Error] in case of failure (also in case the remote function returned an error)
     */
    fun callMethod(message: MethodCall, timeout: ULong): MethodReply

    /**
     * Calls method on the D-Bus object asynchronously
     *
     * @param message Message representing an async method call
     * @param asyncReplyCallback Handler for the async reply
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
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): PendingAsyncCall

    /**
     * Calls method on the D-Bus object asynchronously, with custom timeout
     *
     * @param message Message representing an async method call
     * @param asyncReplyCallback Handler for the async reply
     * @param timeout Method call timeout (in microseconds)
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
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall

    /**
     * Calls method on the D-Bus object asynchronously
     *
     * @param message Message representing an async method call
     * @param Tag denoting a std::future-based overload
     * @return Future object providing access to the future method reply message
     *
     * This is a std::future-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided future object will be set to contain the reply (or [com.monkopedia.sdbus.Error]
     * in case the remote method threw an exception).
     *
     * The default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use higher-level API defined below.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    suspend fun callMethodAsync(message: MethodCall): MethodReply

    /**
     * Calls method on the D-Bus object asynchronously, with custom timeout
     *
     * @param message Message representing an async method call
     * @param timeout Method call timeout
     * @param Tag denoting a std::future-based overload
     * @return Future object providing access to the future method reply message
     *
     * This is a std::future-based way of asynchronously calling a remote D-Bus method.
     *
     * The call itself is non-blocking. It doesn't wait for the reply. Once the reply arrives,
     * the provided future object will be set to contain the reply (or [com.monkopedia.sdbus.Error]
     * in case the remote method threw an exception, or the call timed out).
     *
     * If timeout is zero, the default D-Bus method call timeout is used. See IConnection::getMethodCallTimeout().
     *
     * Note: To avoid messing with messages, use higher-level API defined below.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    suspend fun callMethodAsync(message: MethodCall, timeout: ULong): MethodReply

    /**
     * Registers a handler for the desired signal emitted by the D-Bus object
     *
     * @param interfaceName Name of an interface that the signal belongs to
     * @param signalName Name of the signal
     * @param signalHandler Callback that implements the body of the signal handler
     *
     * @return [Resource] handle to the registration
     *
     * A signal can be subscribed to and unsubscribed from at any time during proxy
     * lifetime. The subscription is active immediately after the call. The lifetime
     * of the subscription is bound to the lifetime of the resource object. The subscription
     * is unregistered by releasing the resource object.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource
}

/********************************************/
/**
 * PendingAsyncCall represents a simple handle type to cancel the delivery
 * of the asynchronous D-Bus call result to the application.
 *
 * The handle is lifetime-independent from the originating Proxy object.
 * It's safe to call its methods even after the Proxy has gone.
 *
 ***********************************************/
expect class PendingAsyncCall : Resource {

    /**
     * Cancels the delivery of the pending asynchronous call result
     *
     * This function effectively removes the callback handler registered to the
     * async D-Bus method call result delivery. Does nothing if the call was
     * completed already, or if the originating Proxy object has gone meanwhile.
     */
    override fun release()

    /**
     * Answers whether the asynchronous call is still pending
     *
     * @return True if the call is pending, false if the call has been fully completed
     *
     * Pending call in this context means a call whose results have not arrived, or
     * have arrived and are currently being processed by the callback handler.
     */
    fun isPending(): Boolean
}

// Out-of-line member definitions

inline fun Proxy.callMethod(message: MethodCall, timeout: Duration): MethodReply =
    callMethod(message, timeout.inWholeMicroseconds.toULong())

inline fun Proxy.callMethodAsync(
    message: MethodCall,
    noinline asyncReplyCallback: AsyncReplyHandler,
    timeout: Duration
): PendingAsyncCall =
    callMethodAsync(message, asyncReplyCallback, timeout.inWholeMicroseconds.toULong())

suspend inline fun Proxy.callMethodAsync(message: MethodCall, timeout: Duration): MethodReply =
    callMethodAsync(message, timeout.inWholeMicroseconds.toULong())

/**
 * Gets value of a property of the D-Bus object asynchronously
 *
 * @param propertyName Name of the property
 * @return A helper object for convenient asynchronous getting of property value
 *
 * This is a high-level, convenience way of reading D-Bus property values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * ```
 * val state: Int = proxy.getPropertyAsync(PropertyName("state")))
 *   .onInterface(InterfaceName("com.kristler.foo"))
 *   .get()
 * ```
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun Proxy.getPropertyAsync(propertyName: PropertyName): AsyncPropertyGetter =
    AsyncPropertyGetter(this, propertyName)

/**
 * Sets value of a property of the D-Bus object asynchronously
 *
 * @param propertyName Name of the property
 * @return A helper object for convenient asynchronous setting of property value
 *
 * This is a high-level, convenience way of writing D-Bus property values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * ```
 * val state: Int = ...
 * proxy.setPropertyAsync(PropertyName("state"))
 *   .onInterface(InterfaceName("com.kristler.foo"))
 *   .toValue(state)
 * ```
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun Proxy.setPropertyAsync(propertyName: PropertyName): AsyncPropertySetter =
    AsyncPropertySetter(this, propertyName)

/**
 * Gets values of all properties of the D-Bus object
 *
 * @return A helper object for convenient getting of properties' values
 *
 * This is a high-level, convenience way of reading D-Bus properties' values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * ```
 * val props = proxy.getAllProperties().onInterface(InterfaceName("com.kistler.foo"))
 * ```
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun Proxy.getAllProperties(): AllPropertiesGetter = AllPropertiesGetter(this)

/**
 * Gets values of all properties of the D-Bus object asynchronously
 *
 * @return A helper object for convenient asynchronous getting of properties' values
 *
 * This is a high-level, convenience way of reading D-Bus properties' values that abstracts
 * from the D-Bus message concept.
 *
 * Example of use:
 * ```
 * val props = object.getAllPropertiesAsync().onInterface(InterfaceName("com.kistler.foo"))
 *   getResult();
 * ```
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */

inline fun Proxy.getAllPropertiesAsync(): AsyncAllPropertiesGetter = AsyncAllPropertiesGetter(this)

/**
 * Creates a proxy object for a specific remote D-Bus object
 *
 * @param connection D-Bus connection to be used by the proxy object
 * @param destination Bus name that provides the remote D-Bus object
 * @param objectPath Path of the remote D-Bus object
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
 * ```
 * val proxy = createProxy(connection, ServiceName("com.kistler.foo"), ObjectPath("/com/kistler/foo"))
 * ```
 */
expect fun createProxy(
    connection: Connection,
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean = false
): Proxy

/**
 * Creates a proxy object for a specific remote D-Bus object
 *
 * @param destination Bus name that provides the remote D-Bus object
 * @param objectPath Path of the remote D-Bus object
 * @return Pointer to the object proxy instance
 *
 * No D-Bus connection is provided here, so the object proxy will create and manage
 * his own connection, and will automatically start an event loop upon that connection
 * in a separate internal thread. Handlers for incoming signals and asynchronous
 * method replies will be executed in the context of that thread.
 *
 * Code example:
 * ```
 * val proxy = createProxy(ServiceName("com.kistler.foo"), ObjectPath("/com/kistler/foo"))
 * ```
 */
expect fun createProxy(
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean = false
): Proxy
