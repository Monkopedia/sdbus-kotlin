@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus
import kotlin.time.Duration
import kotlin.time.Duration.Companion
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

/********************************************/
/**
 * @class IConnection
 *
 * An interface to D-Bus bus connection. Incorporates implementation
 * of both synchronous and asynchronous D-Bus I/O event loop.
 *
 * All methods throw sdbus::Error in case of failure. All methods in
 * this class are thread-aware, but not thread-safe.
 *
 ***********************************************/
interface IConnection : Resource {

    /*!
     * @brief Enters I/O event loop on this bus connection in a separate thread
     *
     * The same as enterEventLoop, except that it doesn't block
     * because it runs the loop in a separate, internally managed thread.
     */
    fun enterEventLoopAsync()

    /*!
     * @brief Leaves the I/O event loop running on this bus connection
     *
     * This causes the loop to exit and frees the thread serving the loop
     *
     * @throws sdbus::Error in case of failure
     */
    suspend fun leaveEventLoop()

    /*!
     * @brief Returns fd's, I/O events and timeout data to be used in an external event loop
     *
     * This function is useful to hook up a bus connection object with an
     * external (like GMainLoop, boost::asio, etc.) or manual event loop
     * involving poll() or a similar I/O polling call.
     *
     * Before **each** invocation of the I/O polling call, this function
     * should be invoked. Returned PollData::fd file descriptor should
     * be polled for the events indicated by PollData::events, and the I/O
     * call should block for that up to the returned PollData::timeout.
     *
     * Additionally, returned PollData::eventFd should be polled for POLLIN
     * events.
     *
     * After each I/O polling call the bus connection needs to process
     * incoming or outgoing data, by invoking processPendingEvent().
     *
     * Note that the returned timeout should be considered only a maximum
     * sleeping time. It is permissible (and even expected) that shorter
     * timeouts are used by the calling program, in case other event sources
     * are polled in the same event loop. Note that the returned time-value
     * is absolute, based of CLOCK_MONOTONIC and specified in microseconds.
     * Use PollData::getPollTimeout() to have the timeout value converted
     * in a form that can be passed to poll(2).
     *
     * The bus connection conveniently integrates sd-event event loop.
     * To attach the bus connection to an sd-event event loop, use
     * attachSdEventLoop() function.
     *
     * @throws sdbus::Error in case of failure
     */
    fun getEventLoopPollData(): PollData

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
     * @brief Sets general method call timeout
     *
     * @param[in] timeout Timeout value in microseconds
     *
     * General method call timeout is used for all method calls upon this connection.
     * Method call-specific timeout overrides this general setting.
     *
     * Supported by libsystemd>=v240.
     *
     * @throws sdbus::Error in case of failure
     */
    fun setMethodCallTimeout(timeout: ULong)

    /*!
     * @copydoc IConnection::setMethodCallTimeout(uint64_t)
     */
    fun setMethodCallTimeout(timeout: Duration)

    /*!
     * @brief Gets general method call timeout
     *
     * @return Timeout value in microseconds
     *
     * Supported by libsystemd>=v240.
     *
     * @throws sdbus::Error in case of failure
     */
    fun getMethodCallTimeout(): ULong

    /*!
     * @brief Adds an ObjectManager at the specified D-Bus object path
     * @param[in] objectPath Object path at which the ObjectManager interface shall be installed
     * @return Slot handle owning the registration
     *
     * Creates an ObjectManager interface at the specified object path on
     * the connection. This is a convenient way to interrogate a connection
     * to see what objects it has.
     *
     * This call returns an owning slot. The lifetime of the ObjectManager
     * interface is bound to the lifetime of the returned slot instance.
     *
     * Another, recommended way to add object managers is directly through IObject API.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addObjectManager(objectPath: ObjectPath): Resource

    /*!
     * @brief Installs a match rule for messages received on this bus connection
     *
     * @param[in] match Match expression to filter incoming D-Bus message
     * @param[in] callback Callback handler to be called upon processing an inbound D-Bus message matching the rule
     * @return RAII-style slot handle representing the ownership of the subscription
     *
     * The method installs a match rule for messages received on the specified bus connection.
     * The syntax of the match rule expression passed in match is described in the D-Bus specification.
     * The specified handler function callback is called for each incoming message matching the specified
     * expression. The match is installed synchronously when connected to a bus broker, i.e. the call
     * sends a control message requesting the match to be added to the broker and waits until the broker
     * confirms the match has been installed successfully.
     *
     * The lifetime of the match rule is bound to the lifetime of the returned slot instance. Destroying
     * the slot instance implies uninstalling of the match rule from the bus connection.
     *
     * For more information, consult `man sd_bus_add_match`.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addMatch(match: String, callback: MessageHandler): Resource

    /*!
     * @brief Asynchronously installs a match rule for messages received on this bus connection
     *
     * @param[in] match Match expression to filter incoming D-Bus message
     * @param[in] callback Callback handler to be called upon processing an inbound D-Bus message matching the rule
     * @param[in] installCallback Callback handler to be called upon processing an inbound D-Bus message matching the rule
     * @return RAII-style slot handle representing the ownership of the subscription
     *
     * This method operates the same as `addMatch()` above, just that it installs the match rule asynchronously,
     * in a non-blocking fashion. A request is sent to the broker, but the call does not wait for a response.
     * The `installCallback' callable is called when the response is later received, with the response message
     * from the broker as parameter. If it's an empty function object, a default implementation is used that
     * terminates the bus connection should installing the match fail.
     *
     * The lifetime of the match rule is bound to the lifetime of the returned slot instance. Destroying
     * the slot instance implies the uninstalling of the match rule from the bus connection.
     *
     * For more information, consult `man sd_bus_add_match_async`.
     *
     * @throws sdbus::Error in case of failure
     */
    fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ): Resource

    /*!
     * @brief Retrieves the unique name of a connection. E.g. ":1.xx"
     *
     * @throws sdbus::Error in case of failure
     */
    fun getUniqueName(): BusName

    /*!
     * @brief Requests a well-known D-Bus service name on a bus
     *
     * @param[in] name Name to request
     *
     * @throws sdbus::Error in case of failure
     */
    fun requestName(name: ServiceName)

    /*!
     * @brief Releases an acquired well-known D-Bus service name on a bus
     *
     * @param[in] name Name to release
     *
     * @throws sdbus::Error in case of failure
     */
    fun releaseName(name: ServiceName)

    /*!
     * @struct PollData
     *
     * Carries poll data needed for integration with external event loop implementations.
     *
     * See getEventLoopPollData() for more info.
     */
    data class PollData(
        /*!
         * The read fd to be monitored by the event loop.
         */
        val fd: Int = 0,

        /*!
         * The events to use for poll(2) alongside fd.
         */
        val events: Short = 0,

        /*!
         * Absolute timeout value in microseconds, based of CLOCK_MONOTONIC.
         *
         * Call getPollTimeout() to get timeout recalculated to relative timeout that can be passed to poll(2).
         */
        val timeout: Duration? = null,

        /*!
         * An additional event fd to be monitored by the event loop for POLLIN events.
         */
        val eventFd: Int = 0
    ) {

        /*!
         * Returns the timeout as relative value from now.
         *
         * Returned value is std::chrono::microseconds::max() if the timeout is indefinite.
         *
         * @return Relative timeout as a time duration
         */
        fun getRelativeTimeout(): Duration = when (timeout) {
            null, Duration.ZERO -> Companion.ZERO
            Companion.INFINITE -> Companion.INFINITE
            else -> (timeout - now()).coerceAtLeast(Companion.ZERO)
        }

        /*!
         * Returns relative timeout in the form which can be passed as argument 'timeout' to poll(2)
         *
         * @return -1 if the timeout is indefinite. 0 if the poll(2) shouldn't block.
         *         An integer in milliseconds otherwise.
         */
        fun getPollTimeout(): Int {
            val relativeTimeout = getRelativeTimeout()

            return if (relativeTimeout == Duration.INFINITE) {
                -1
            } else {
                relativeTimeout.inWholeMilliseconds.toInt()
            }
        }
    }
}
internal expect inline fun now(): Duration

/*!
 * @brief Creates/opens D-Bus session bus connection when in a user context, and a system bus connection, otherwise.
 *
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createBusConnection(): IConnection

/*!
 * @brief Creates/opens D-Bus session bus connection with a name when in a user context, and a system bus connection with a name, otherwise.
 *
 * @param[in] name Name to request on the connection after its opening
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createBusConnection(name: ServiceName): IConnection

/*!
 * @brief Creates/opens D-Bus system bus connection
 *
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createSystemBusConnection(): IConnection

/*!
 * @brief Creates/opens D-Bus system bus connection with a name
 *
 * @param[in] name Name to request on the connection after its opening
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createSystemBusConnection(name: ServiceName): IConnection

/*!
 * @brief Creates/opens D-Bus session bus connection
 *
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createSessionBusConnection(): IConnection

/*!
 * @brief Creates/opens D-Bus session bus connection with a name
 *
 * @param[in] name Name to request on the connection after its opening
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createSessionBusConnection(name: ServiceName): IConnection

/*!
 * @brief Creates/opens D-Bus session bus connection at a custom address
 *
 * @param[in] address ";"-separated list of addresses of bus brokers to try to connect
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 *
 * Consult manual pages for `sd_bus_set_address` of the underlying sd-bus library for more information.
 */
expect fun createSessionBusConnectionWithAddress(address: String): IConnection

/*!
 * @brief Creates/opens D-Bus system connection on a remote host using ssh
 *
 * @param[in] host Name of the host to connect
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createRemoteSystemBusConnection(host: String): IConnection

/*!
 * @brief Opens direct D-Bus connection at a custom address
 *
 * @param[in] address ";"-separated list of addresses of bus brokers to try to connect to
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createDirectBusConnection(address: String): IConnection

/*!
 * @brief Opens direct D-Bus connection at the given file descriptor
 *
 * @param[in] fd File descriptor used to communicate directly from/to a D-Bus server
 * @return Connection instance
 *
 * The underlying sdbus-c++ connection instance takes over ownership of fd, so the caller can let it go.
 * If, however, the call throws an exception, the ownership of fd remains with the caller.
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createDirectBusConnection(fd: Int): IConnection

/*!
 * @brief Opens direct D-Bus connection at fd as a server
 *
 * @param[in] fd File descriptor to use for server DBus connection
 * @return Server connection instance
 *
 * This creates a new, custom bus object in server mode. One can then call createDirectBusConnection()
 * on client side to connect to this bus.
 *
 * The underlying sdbus-c++ connection instance takes over ownership of fd, so the caller can let it go.
 * If, however, the call throws an exception, the ownership of fd remains with the caller.
 *
 * @throws sdbus::Error in case of failure
 */
expect fun createServerBus(fd: Int): IConnection

/*!
 * @brief Creates sdbus-c++ bus connection representation out of underlying sd_bus instance
 *
 * @param[in] bus File descriptor to use for server DBus connection
 * @return Connection instance
 *
 * This functions is helpful in cases where clients need a custom, tweaked configuration of their
 * bus object. Since sdbus-c++ does not provide C++ API for all bus connection configuration
 * functions of the underlying sd-bus library, clients can use these sd-bus functions themselves
 * to create and configure their sd_bus object, and create sdbus-c++ IConnection on top of it.
 *
 * The IConnection instance assumes unique ownership of the provided bus object. The bus object
 * must have been started by the client before this call.
 * The bus object will get flushed, closed, and unreffed when the IConnection instance is destroyed.
 *
 * @throws sdbus::Error in case of failure
 *
 * Code example:
 * @code
 * sd_bus* bus{};
 * ::sd_bus_new(&bus);
 * ::sd_bus_set_address(bus, address);
 * ::sd_bus_set_anonymous(bus, true);
 * ::sd_bus_start(bus);
 * auto con = sdbus::createBusConnection(bus); // IConnection consumes sd_bus object
 * @endcode
 */
expect fun createBusConnection(bus: CPointer<sd_bus>): IConnection
