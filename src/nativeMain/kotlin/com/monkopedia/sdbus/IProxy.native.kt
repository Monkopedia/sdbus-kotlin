@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.ProxyImpl
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.memScoped
import platform.posix.EINVAL

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
 * @code
 * auto proxy = sdbus::createProxy(connection, "com.kistler.foo", "/com/kistler/foo");
 * @endcode
 */
actual fun createProxy(
    connection: Connection,
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean
): Proxy {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.InternalConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-c++ connection",
        EINVAL
    )

    return ProxyImpl(
        sdbusConnection!!,
        destination,
        objectPath,
        dontRunEventLoopThread = dontRunEventLoopThread
    )
}

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
 * @code
 * auto proxy = sdbus::createProxy("com.kistler.foo", "/com/kistler/foo");
 * @endcode
 */
actual fun createProxy(
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean
): Proxy = memScoped {
    val connection = createBusConnection()

    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.InternalConnection
    assert(sdbusConnection != null)

    ProxyImpl(
        sdbusConnection!!,
        destination,
        objectPath,
        dontRunEventLoopThread = dontRunEventLoopThread
    )
}
