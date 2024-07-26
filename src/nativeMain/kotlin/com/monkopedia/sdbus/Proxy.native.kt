@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.ProxyImpl
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.memScoped
import platform.posix.EINVAL

actual fun createProxy(
    connection: Connection,
    destination: ServiceName,
    objectPath: ObjectPath,
    dontRunEventLoopThread: Boolean
): Proxy {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.InternalConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-kotlin connection",
        EINVAL
    )

    return ProxyImpl(
        sdbusConnection!!,
        destination,
        objectPath,
        dontRunEventLoopThread = dontRunEventLoopThread
    )
}

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
