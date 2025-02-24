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
@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.ProxyImpl
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
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
