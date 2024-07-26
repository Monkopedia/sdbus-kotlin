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

package com.monkopedia.sdbus.internal

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr
import sdbus._SD_BUS_VTABLE_METHOD
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_property
import sdbus.sd_bus_property_get_t
import sdbus.sd_bus_property_set_t
import sdbus.sd_bus_signal_with_names
import sdbus.sd_bus_vtable
import sdbus.sd_bus_vtable_end
import sdbus.sd_bus_vtable_start
import sdbus.sd_bus_writable_property

internal fun createSdBusVTableStartItem(flags: ULong): CValue<sd_bus_vtable> = cValue {
    sd_bus_vtable_start(ptr, flags)
}

internal fun createSdBusVTableMethodItem(
    member: CPointer<ByteVar>,
    signature: CPointer<ByteVar>,
    result: CPointer<ByteVar>,
    paramNames: CPointer<ByteVar>,
    handler: sd_bus_message_handler_t,
    flags: ULong
): CValue<sd_bus_vtable> = cValue {
    this.type = _SD_BUS_VTABLE_METHOD.convert()
    this.flags = flags
    this.x.method.member = member
    this.x.method.signature = signature
    this.x.method.result = result
    this.x.method.handler = handler
    this.x.method.offset = 0.convert()
    this.x.method.names = paramNames
}

internal fun createSdBusVTableSignalItem(
    member: CPointer<ByteVar>,
    signature: CPointer<ByteVar>,
    outnames: CPointer<ByteVar>,
    flags: ULong
): CValue<sd_bus_vtable> = cValue {
    sd_bus_signal_with_names(ptr, member, signature, outnames, flags)
}

internal fun createSdBusVTableReadOnlyPropertyItem(
    member: CPointer<ByteVar>,
    signature: CPointer<ByteVar>,
    getter: sd_bus_property_get_t,
    flags: ULong
): CValue<sd_bus_vtable> = cValue { sd_bus_property(ptr, member, signature, getter, flags) }

internal fun createSdBusVTableWritablePropertyItem(
    member: CPointer<ByteVar>,
    signature: CPointer<ByteVar>,
    getter: sd_bus_property_get_t,
    setter: sd_bus_property_set_t,
    flags: ULong
): CValue<sd_bus_vtable> =
    cValue { sd_bus_writable_property(ptr, member, signature, getter, setter, flags) }

internal fun createSdBusVTableEndItem(): CValue<sd_bus_vtable> = cValue { sd_bus_vtable_end(ptr) }
