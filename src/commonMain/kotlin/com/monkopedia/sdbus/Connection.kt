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
 * An interface to D-Bus bus connection. Incorporates implementation
 * of both synchronous and asynchronous D-Bus I/O event loop.
 *
 * All methods throw [com.monkopedia.sdbus.SdbusException] in case of failure. All methods in
 * this class are thread-aware, but not thread-safe.
 *
 ***********************************************/
interface Connection : Resource {

    /**
     * Starts the I/O event loop on this bus connection
     *
     * The loop runs in a separate, internally managed thread, processing incoming
     * and outgoing D-Bus messages, so this call does not block. The loop runs until
     * [stopEventLoop] is called or the connection is released.
     */
    fun startEventLoop()

    /**
     * Stops the I/O event loop running on this bus connection
     *
     * This causes the loop started by [startEventLoop] to exit, and frees the
     * thread serving the loop
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
     */
    suspend fun stopEventLoop()

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
     * General method call timeout
     *
     * General method call timeout is used for all method calls upon this connection.
     * Method call-specific timeout overrides this general setting.
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
     */
    var methodCallTimeout: Duration

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
     * Another, recommended way to add object managers is directly through the [Object] API:
     * [Object.addObjectManager] installs the ObjectManager at that object's own path.
     *
     * @see Object.addObjectManager
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
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
     * Note: synchronous installation does not imply synchronous callback delivery. Matching messages are
     * still dispatched via the connection's event loop and may be observed with scheduler/runtime delay.
     * In timing-sensitive flows, avoid immediate delivery assumptions.
     *
     * The lifetime of the match rule is bound to the lifetime of the returned resource instance.
     * Releasing the resource instance implies uninstalling of the match rule from the bus connection.
     *
     * For more information, consult `man sd_bus_add_match`.
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
     */
    fun addMatch(match: String, callback: MessageHandler): Resource

    /**
     * The unique name of the connection. E.g. ":1.xx"
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
     */
    val uniqueName: BusName

    /**
     * Requests a well-known D-Bus service name on a bus
     *
     * @param name Name to request
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
     */
    fun requestName(name: ServiceName)

    /**
     * Releases an acquired well-known D-Bus service name on a bus
     *
     * @param name Name to release
     *
     * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
     */
    fun releaseName(name: ServiceName)
}

internal expect fun now(): Duration

/**
 * Creates/opens D-Bus session bus connection when in a user context, and a system bus connection, otherwise.
 *
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createBusConnection(): Connection

/**
 * Creates/opens D-Bus session bus connection with a name when in a user context, and a system bus connection with a name, otherwise.
 *
 * @param name Name to request on the connection after its opening
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createBusConnection(name: ServiceName): Connection

/**
 * Creates/opens D-Bus system bus connection
 *
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createSystemBusConnection(): Connection

/**
 * Creates/opens D-Bus system bus connection with a name
 *
 * @param name Name to request on the connection after its opening
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createSystemBusConnection(name: ServiceName): Connection

/**
 * Creates/opens D-Bus session bus connection
 *
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createSessionBusConnection(): Connection

/**
 * Creates/opens D-Bus session bus connection with a name
 *
 * @param name Name to request on the connection after its opening
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createSessionBusConnection(name: ServiceName): Connection

/**
 * Creates/opens D-Bus session bus connection at a custom address
 *
 * @param address ";"-separated list of addresses of bus brokers to try to connect
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 *
 * Consult manual pages for `sd_bus_set_address` of the underlying sd-bus library for more information.
 */
expect fun createSessionBusConnection(address: String): Connection

/**
 * Opens direct D-Bus connection at a custom address
 *
 * @param address ";"-separated list of addresses of bus brokers to try to connect to
 * @return [Connection] instance
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createDirectBusConnection(address: String): Connection

/**
 * Opens direct D-Bus connection at the given file descriptor
 *
 * Native-only API.
 * On JVM, fd-backed direct connections are not supported by the underlying transport stack.
 *
 * @param fd File descriptor used to communicate directly from/to a D-Bus server
 * @return [Connection] instance
 *
 * The connection adopts (takes over ownership of) the file descriptor held by [fd] without
 * duplicating it: on success, [fd] no longer owns the descriptor (it is detached, and neither
 * releasing nor garbage-collecting [fd] will close it), and the connection closes the
 * descriptor when it is destroyed. If, however, the call throws an exception, the ownership
 * of the descriptor remains with [fd].
 *
 * Use `UnixFd.adopt(rawFd)` to hand off a raw descriptor you own, or `UnixFd(rawFd)` to pass
 * a duplicate while keeping your own descriptor.
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createDirectBusConnection(fd: UnixFd): Connection

/**
 * Opens direct D-Bus connection at fd as a server
 *
 * Native-only API.
 * On JVM, fd-backed server buses are not supported by the underlying transport stack.
 *
 * @param fd File descriptor to use for server DBus connection
 * @return [Connection] server instance
 *
 * This creates a new, custom bus object in server mode. One can then call createDirectBusConnection()
 * on client side to connect to this bus.
 *
 * The connection adopts (takes over ownership of) the file descriptor held by [fd] without
 * duplicating it: on success, [fd] no longer owns the descriptor (it is detached, and neither
 * releasing nor garbage-collecting [fd] will close it), and the connection closes the
 * descriptor when it is destroyed. If, however, the call throws an exception, the ownership
 * of the descriptor remains with [fd].
 *
 * Use `UnixFd.adopt(rawFd)` to hand off a raw descriptor you own, or `UnixFd(rawFd)` to pass
 * a duplicate while keeping your own descriptor.
 *
 * @throws [com.monkopedia.sdbus.SdbusException] in case of failure
 */
expect fun createServerBusConnection(fd: UnixFd): Connection
