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
