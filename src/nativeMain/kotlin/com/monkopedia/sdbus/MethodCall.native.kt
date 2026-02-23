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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus_message
import cnames.structs.sd_bus_slot
import com.monkopedia.sdbus.internal.ISdBus
import com.monkopedia.sdbus.internal.Reference
import kotlin.native.internal.NativePtr
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import sdbus.sd_bus_error
import sdbus.sd_bus_error_free
import sdbus.sd_bus_error_is_set
import sdbus.sd_bus_error_set
import sdbus.sd_bus_message_get_expect_reply
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_message_set_expect_reply

actual class MethodCall internal constructor(
    msg: CPointer<sd_bus_message>?,
    sdbus: ISdBus,
    adoptMessage: Boolean = false
) : Message(msg, sdbus, adoptMessage) {

    internal constructor(sdbus: ISdBus) : this(null, sdbus)

    constructor (o: MethodCall) : this(o.msg, o.sdbus)

    actual fun send(timeout: ULong): MethodReply =
        if (dontExpectReply) sendWithNoReply() else sendWithReply(timeout)

    fun send(callback: sd_bus_message_handler_t, userData: Any?, timeout: ULong): Resource =
        memScoped {
            val slot: CPointer<CPointerVar<sd_bus_slot>> =
                cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
            val userDataRef = userData?.let { StableRef.create(it) }

            val r = sdbus.sd_bus_call_async(
                null,
                slot,
                msg,
                callback,
                userDataRef?.asCPointer(),
                timeout
            )
            sdbusRequire(r < 0, "Failed to call method asynchronously", -r)

            Reference(slot[0]) {
                sdbus.sd_bus_slot_unref(it)
                userDataRef?.dispose()
            }
        }

    actual fun createReply(): MethodReply = memScoped {
        val sdbusReply = cValue<CPointerVar<sd_bus_message>>().getPointer(this)
        val r = sdbus.sd_bus_message_new_method_return(msg, sdbusReply)
        sdbusRequire(r < 0, "Failed to create method reply", -r)

        MethodReply(sdbusReply[0]!!, sdbus, adoptMessage = true)
    }

    actual fun createErrorReply(error: Error): MethodReply = memScoped {
        val sdbusError = sdBusNullError()
        sd_bus_error_set(sdbusError, error.name, error.errorMessage)

        val sdbusErrorReply =
            cValuesOf(interpretCPointer<sd_bus_message>(NativePtr.NULL)).getPointer(this)
        val r = sdbus.sd_bus_message_new_method_error(msg, sdbusErrorReply, sdbusError)
        sdbusRequire(r < 0, "Failed to create method error reply", -r)

        MethodReply(sdbusErrorReply[0]!!, sdbus, adoptMessage = true)
    }

    actual var dontExpectReply: Boolean
        get() {
            val r = sd_bus_message_get_expect_reply(msg)
            sdbusRequire(r < 0, "Failed to get the dont-expect-reply flag", -r)
            return r == 0
        }
        set(value) {
            val r = sd_bus_message_set_expect_reply(msg, if (value) 0 else 1)
            sdbusRequire(r < 0, "Failed to set the dont-expect-reply flag", -r)
        }

    private fun sendWithReply(timeout: ULong = 0u): MethodReply = memScoped {
        val sdbusError = sdBusNullError()

        val sdbusReply =
            cValuesOf(interpretCPointer<sd_bus_message>(NativePtr.NULL)).getPointer(this)
        val r = sdbus.sd_bus_call(null, msg, timeout, sdbusError, sdbusReply)

        if (sd_bus_error_is_set(sdbusError) != 0) {
            sdbusError[0].apply {
                throw Error(name?.toKString()!!, message?.toKString()!!)
            }
        }

        sdbusRequire(r < 0, "Failed to call method", -r)

        MethodReply(sdbusReply[0]!!, sdbus, adoptMessage = true)
    }

    private fun MemScope.sdBusNullError() = cValue<sd_bus_error> {
        name = null
        message = null
        _need_free = 0
    }.getPointer(this).also {
        defer { sd_bus_error_free(it) }
    }

    private fun sendWithNoReply(): MethodReply {
        val r = sdbus.sd_bus_send(null, msg, null)
        sdbusRequire(r < 0, "Failed to call method with no reply", -r)

        return MethodReply(sdbus); // No reply
    }
}
