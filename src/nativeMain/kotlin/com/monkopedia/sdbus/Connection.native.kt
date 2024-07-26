/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus
import com.monkopedia.sdbus.internal.ConnectionImpl
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.defaultConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.privateConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.remoteConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.serverConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.sessionConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.systemConnection
import com.monkopedia.sdbus.internal.Reference
import com.monkopedia.sdbus.internal.SdBus
import kotlin.time.Duration
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

actual fun createBusConnection(): Connection = defaultConnection(SdBus())

actual fun createBusConnection(name: ServiceName): Connection =
    defaultConnection(SdBus()).also { it.requestName(name) }

actual fun createSystemBusConnection(): Connection = systemConnection(SdBus())

actual fun createSystemBusConnection(name: ServiceName): Connection =
    defaultConnection(SdBus()).also { it.requestName(name) }

actual fun createSessionBusConnection(): Connection = sessionConnection(SdBus())

actual fun createSessionBusConnection(name: ServiceName): Connection =
    sessionConnection(SdBus()).also { it.requestName(name) }

actual fun createSessionBusConnectionWithAddress(address: String): Connection =
    sessionConnection(SdBus(), address)

actual fun createRemoteSystemBusConnection(host: String): Connection =
    remoteConnection(SdBus(), host)

actual fun createDirectBusConnection(address: String): Connection =
    privateConnection(SdBus(), address)

actual fun createDirectBusConnection(fd: Int): Connection = privateConnection(SdBus(), fd)

actual fun createServerBus(fd: Int): Connection = serverConnection(SdBus(), fd)

internal fun createBusConnection(bus: CPointer<sd_bus>): Connection =
    ConnectionImpl(SdBus(), Reference(bus) {})

internal actual inline fun now(): Duration = com.monkopedia.sdbus.internal.now()
