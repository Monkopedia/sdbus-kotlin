package com.monkopedia.sdbus

import cnames.structs.sd_bus
import com.monkopedia.sdbus.internal.Connection.Companion.Connection
import com.monkopedia.sdbus.internal.Connection.Companion.defaultConnection
import com.monkopedia.sdbus.internal.Connection.Companion.privateConnection
import com.monkopedia.sdbus.internal.Connection.Companion.remoteConnection
import com.monkopedia.sdbus.internal.Connection.Companion.serverConnection
import com.monkopedia.sdbus.internal.Connection.Companion.sessionConnection
import com.monkopedia.sdbus.internal.Connection.Companion.systemConnection
import com.monkopedia.sdbus.internal.SdBus
import kotlin.time.Duration
import kotlinx.cinterop.CPointer

/*!
 * @brief Creates/opens D-Bus session bus connection when in a user context, and a system bus connection, otherwise.
 *
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createBusConnection(): IConnection = defaultConnection(SdBus())

/*!
 * @brief Creates/opens D-Bus session bus connection with a name when in a user context, and a system bus connection with a name, otherwise.
 *
 * @param[in] name Name to request on the connection after its opening
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createBusConnection(name: ServiceName): IConnection =
    defaultConnection(SdBus()).also { it.requestName(name) }

/*!
 * @brief Creates/opens D-Bus system bus connection
 *
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createSystemBusConnection(): IConnection = systemConnection(SdBus())

/*!
 * @brief Creates/opens D-Bus system bus connection with a name
 *
 * @param[in] name Name to request on the connection after its opening
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createSystemBusConnection(name: ServiceName): IConnection =
    defaultConnection(SdBus()).also { it.requestName(name) }

/*!
 * @brief Creates/opens D-Bus session bus connection
 *
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createSessionBusConnection(): IConnection = sessionConnection(SdBus())

/*!
 * @brief Creates/opens D-Bus session bus connection with a name
 *
 * @param[in] name Name to request on the connection after its opening
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createSessionBusConnection(name: ServiceName): IConnection =
    sessionConnection(SdBus()).also { it.requestName(name) }

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
actual fun createSessionBusConnectionWithAddress(address: String): IConnection =
    sessionConnection(SdBus(), address)

/*!
 * @brief Creates/opens D-Bus system connection on a remote host using ssh
 *
 * @param[in] host Name of the host to connect
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createRemoteSystemBusConnection(host: String): IConnection =
    remoteConnection(SdBus(), host)

/*!
 * @brief Opens direct D-Bus connection at a custom address
 *
 * @param[in] address ";"-separated list of addresses of bus brokers to try to connect to
 * @return Connection instance
 *
 * @throws sdbus::Error in case of failure
 */
actual fun createDirectBusConnection(address: String): IConnection =
    privateConnection(SdBus(), address)

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
actual fun createDirectBusConnection(fd: Int): IConnection = privateConnection(SdBus(), fd)

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
actual fun createServerBus(fd: Int): IConnection = serverConnection(SdBus(), fd)

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
fun createBusConnection(bus: CPointer<sd_bus>): IConnection = Connection(SdBus(), bus)

internal actual inline fun now(): Duration = com.monkopedia.sdbus.internal.now()
