@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import cnames.structs.sd_bus_message
import cnames.structs.sd_bus_slot
import com.monkopedia.sdbus.internal.ISdBus
import com.monkopedia.sdbus.internal.Reference
import com.monkopedia.sdbus.internal.Slot
import kotlin.native.internal.NativePtr
import kotlinx.cinterop.Arena
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
import sdbus.sd_bus_slot_unref
import sdbus.uint64_t

class MethodCall private constructor(msg_: CPointer<sd_bus_message>?, sdbus_: ISdBus, real: Int) :
    Message(msg_, sdbus_, real) {

    constructor(sdbus: ISdBus) :
        this(null, sdbus, 0)

    constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus) :
        this(msg, sdbus, 0) {
        sdbus.sd_bus_message_ref(msg)
    }

    constructor (o: MethodCall) : this(o.msg_, o.sdbus_)
    constructor(
        msg: CPointer<sd_bus_message>,
        sdbus: ISdBus,
        adopt_message: adopt_message_t
    ) : this(msg, sdbus, 0)

    fun send(timeout: uint64_t): MethodReply {
        return if (!doesntExpectReply()) sendWithReply(timeout) else sendWithNoReply()
    }

    fun send(
        callback: sd_bus_message_handler_t,
        userData: Any?,
        timeout: uint64_t,
        t: return_slot_t
    ): Slot = run {
        val arena = Arena()
        val slot: CPointer<CPointerVar<sd_bus_slot>> =
            cValue<CPointerVar<sd_bus_slot>>().getPointer(arena)
        val userDataRef = userData?.let { StableRef.create(it) }

        val r = sdbus_.sd_bus_call_async(
            null,
            slot,
            msg_,
            callback,
            userDataRef?.asCPointer(),
            timeout
        );
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to call method asynchronously", -r);

        Reference(slot[0]) {
            sd_bus_slot_unref(it)
            userDataRef?.dispose()
            arena.clear()
        }
    }

    fun createReply(): MethodReply = memScoped {
        val sdbusReply = cValue<CPointerVar<sd_bus_message>>().getPointer(this)
        val r = sdbus_.sd_bus_message_new_method_return(msg_, sdbusReply)
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to create method reply", -r);

        MethodReply(sdbusReply[0]!!, sdbus_, adopt_message)
    }

    fun createErrorReply(error: Error): MethodReply = memScoped {
        val sdbusError = sdBusNullError()
        sd_bus_error_set(sdbusError, error.name, error.errorMessage)

        val sdbusErrorReply =
            cValuesOf(interpretCPointer<sd_bus_message>(NativePtr.NULL)).getPointer(this)
        val r = sdbus_.sd_bus_message_new_method_error(msg_, sdbusErrorReply, sdbusError);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to create method error reply", -r);

        MethodReply(sdbusErrorReply[0]!!, sdbus_, adopt_message)
    }

    fun dontExpectReply() {
        val r = sd_bus_message_set_expect_reply(msg_, 0);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to set the dont-expect-reply flag", -r);
    }

    fun doesntExpectReply(): Boolean {
        val r = sd_bus_message_get_expect_reply(msg_);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to get the dont-expect-reply flag", -r);
        return r == 0;
    }

    private fun sendWithReply(timeout: uint64_t = 0u): MethodReply = memScoped {
        val sdbusError = sdBusNullError()

        val sdbusReply =
            cValuesOf(interpretCPointer<sd_bus_message>(NativePtr.NULL)).getPointer(this)
        val r = sdbus_.sd_bus_call(null, msg_, timeout, sdbusError, sdbusReply);

        if (sd_bus_error_is_set(sdbusError) != 0) {
            sdbusError[0].apply {
                throw Error(name?.toKString()!!, message?.toKString()!!);
            }
        }

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to call method", -r);

        MethodReply(sdbusReply[0]!!, sdbus_, adopt_message);
    }

    private fun MemScope.sdBusNullError() = cValue<sd_bus_error> {
        name = null
        message = null
        _need_free = 0
    }.getPointer(this).also {
        defer { sd_bus_error_free(it) }
    }

    private fun sendWithNoReply(): MethodReply {
        val r = sdbus_.sd_bus_send(null, msg_, null);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to call method with no reply", -r);

        return MethodReply(sdbus_); // No reply
    }
}
