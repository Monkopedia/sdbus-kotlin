
package com.monkopedia.sdbus

import kotlin.time.Duration

/********************************************/
/**
 * An interface to D-Bus bus connection. Incorporates implementation
 * of both synchronous and asynchronous D-Bus I/O event loop.
 *
 * All methods throw [com.monkopedia.sdbus.Error] in case of failure. All methods in
 * this class are thread-aware, but not thread-safe.
 *
 ***********************************************/
interface Connection : Resource {

    /**
     * Enters I/O event loop on this bus connection in a separate thread
     *
     * The same as enterEventLoop, except that it doesn't block
     * because it runs the loop in a separate, internally managed thread.
     */
    fun enterEventLoopAsync()

    /**
     * Leaves the I/O event loop running on this bus connection
     *
     * This causes the loop to exit and frees the thread serving the loop
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    suspend fun leaveEventLoop()

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
     * Sets general method call timeout
     *
     * @param timeout Timeout value in microseconds
     *
     * General method call timeout is used for all method calls upon this connection.
     * Method call-specific timeout overrides this general setting.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun setMethodCallTimeout(timeout: Duration)

    /**
     * Gets general method call timeout
     *
     * @return Timeout value in microseconds
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun getMethodCallTimeout(): Duration

    /**
     * Adds an ObjectManager at the specified D-Bus object path
     * @param objectPath Object path at which the ObjectManager interface shall be installed
     * @return [Resource] handle owning the registration
     *
     * Creates an ObjectManager interface at the specified object path on
     * the connection. This is a convenient way to interrogate a connection
     * to see what objects it has.
     *
     * This call returns an owning resource. The lifetime of the ObjectManager
     * interface is bound to the lifetime of the returned resource instance.
     *
     * Another, recommended way to add object managers is directly through [Object] API.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun addObjectManager(objectPath: ObjectPath): Resource

    /**
     * Installs a match rule for messages received on this bus connection
     *
     * @param match Match expression to filter incoming D-Bus message
     * @param callback Callback handler to be called upon processing an inbound D-Bus message matching the rule
     * @return [Resource] handle owning the registration
     *
     * The method installs a match rule for messages received on the specified bus connection.
     * The syntax of the match rule expression passed in match is described in the D-Bus specification.
     * The specified handler function callback is called for each incoming message matching the specified
     * expression. The match is installed synchronously when connected to a bus broker, i.e. the call
     * sends a control message requesting the match to be added to the broker and waits until the broker
     * confirms the match has been installed successfully.
     *
     * The lifetime of the match rule is bound to the lifetime of the returned resource instance.
     * Releasing the resource instance implies uninstalling of the match rule from the bus connection.
     *
     * For more information, consult `man sd_bus_add_match`.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun addMatch(match: String, callback: MessageHandler): Resource

    /**
     * Asynchronously installs a match rule for messages received on this bus connection
     *
     * @param match Match expression to filter incoming D-Bus message
     * @param callback Callback handler to be called upon processing an inbound D-Bus message matching the rule
     * @param installCallback Callback handler to be called upon processing an inbound D-Bus message matching the rule
     * @return [Resource] handle owning the registration
     *
     * This method operates the same as `addMatch()` above, just that it installs the match rule asynchronously,
     * in a non-blocking fashion. A request is sent to the broker, but the call does not wait for a response.
     * The `installCallback' callable is called when the response is later received, with the response message
     * from the broker as parameter. If it's an empty function object, a default implementation is used that
     * terminates the bus connection should installing the match fail.
     *
     * The lifetime of the match rule is bound to the lifetime of the returned resource instance.
     * Releasing the slot instance implies the uninstalling of the match rule from the bus
     * connection.
     *
     * For more information, consult `man sd_bus_add_match_async`.
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ): Resource

    /**
     * Retrieves the unique name of a connection. E.g. ":1.xx"
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun getUniqueName(): BusName

    /**
     * Requests a well-known D-Bus service name on a bus
     *
     * @param name Name to request
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun requestName(name: ServiceName)

    /**
     * Releases an acquired well-known D-Bus service name on a bus
     *
     * @param name Name to release
     *
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun releaseName(name: ServiceName)
}

internal expect inline fun now(): Duration

/**
 * Creates/opens D-Bus session bus connection when in a user context, and a system bus connection, otherwise.
 *
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createBusConnection(): Connection

/**
 * Creates/opens D-Bus session bus connection with a name when in a user context, and a system bus connection with a name, otherwise.
 *
 * @param name Name to request on the connection after its opening
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createBusConnection(name: ServiceName): Connection

/**
 * Creates/opens D-Bus system bus connection
 *
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createSystemBusConnection(): Connection

/**
 * Creates/opens D-Bus system bus connection with a name
 *
 * @param name Name to request on the connection after its opening
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createSystemBusConnection(name: ServiceName): Connection

/**
 * Creates/opens D-Bus session bus connection
 *
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createSessionBusConnection(): Connection

/**
 * Creates/opens D-Bus session bus connection with a name
 *
 * @param name Name to request on the connection after its opening
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createSessionBusConnection(name: ServiceName): Connection

/**
 * Creates/opens D-Bus session bus connection at a custom address
 *
 * @param address ";"-separated list of addresses of bus brokers to try to connect
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 *
 * Consult manual pages for `sd_bus_set_address` of the underlying sd-bus library for more information.
 */
expect fun createSessionBusConnectionWithAddress(address: String): Connection

/**
 * Creates/opens D-Bus system connection on a remote host using ssh
 *
 * @param host Name of the host to connect
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createRemoteSystemBusConnection(host: String): Connection

/**
 * Opens direct D-Bus connection at a custom address
 *
 * @param address ";"-separated list of addresses of bus brokers to try to connect to
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createDirectBusConnection(address: String): Connection

/**
 * Opens direct D-Bus connection at the given file descriptor
 *
 * @param fd File descriptor used to communicate directly from/to a D-Bus server
 * @return [Connection] instance
 *
 * The underlying sdbus-kotlin connection instance takes over ownership of fd, so the caller can let it go.
 * If, however, the call throws an exception, the ownership of fd remains with the caller.
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createDirectBusConnection(fd: Int): Connection

/**
 * Opens direct D-Bus connection at fd as a server
 *
 * @param fd File descriptor to use for server DBus connection
 * @return [Connection] server instance
 *
 * This creates a new, custom bus object in server mode. One can then call createDirectBusConnection()
 * on client side to connect to this bus.
 *
 * The underlying sdbus-kotlin connection instance takes over ownership of fd, so the caller can let it go.
 * If, however, the call throws an exception, the ownership of fd remains with the caller.
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
expect fun createServerBus(fd: Int): Connection
