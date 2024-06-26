@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_creds
import cnames.structs.sd_bus_message
import cnames.structs.sd_bus_slot
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.gid_tVar
import sdbus.pid_tVar
import sdbus.sd_bus_error
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_vtable
import sdbus.uid_tVar
import sdbus.uint64_t
import sdbus.uint64_tVar
import sdbus.uint8_t

internal interface ISdBus {
    data class PollData(var fd: Int = 0, var events: Short = 0, var timeout_usec: uint64_t = 0u)

    fun sd_bus_message_ref(m: CValuesRef<sd_bus_message>?): CPointer<sd_bus_message>?
    fun sd_bus_message_unref(m: CValuesRef<sd_bus_message>?): CPointer<sd_bus_message>?

    fun sd_bus_send(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<sd_bus_message>?,
        cookie: CValuesRef<uint64_tVar>?
    ): Int

    fun sd_bus_call(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<sd_bus_message>?,
        usec: uint64_t,
        ret_error: CValuesRef<sd_bus_error>?,
        reply: CValuesRef<CPointerVar<sd_bus_message>>?
    ): Int

    fun sd_bus_call_async(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        m: CValuesRef<sd_bus_message>?,
        callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?,
        usec: uint64_t
    ): Int

    fun sd_bus_message_new(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        type: uint8_t
    ): Int

    fun sd_bus_message_new_method_call(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        destination: String?,
        path: String?,
        `interface`: String?,
        member: String?
    ): Int

    fun sd_bus_message_new_signal(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        path: String?,
        `interface`: String?,
        member: String?
    ): Int

    fun sd_bus_message_new_method_return(
        call: CValuesRef<sd_bus_message>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?
    ): Int

    fun sd_bus_message_new_method_error(
        call: CValuesRef<sd_bus_message>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        e: CValuesRef<sd_bus_error>?
    ): Int

    fun sd_bus_set_method_call_timeout(bus: CValuesRef<sd_bus>?, usec: uint64_t): Int
    fun sd_bus_get_method_call_timeout(bus: CValuesRef<sd_bus>?, ret: CValuesRef<uint64_tVar>?): Int

    fun sd_bus_emit_properties_changed_strv(
        bus: CValuesRef<sd_bus>?,
        path: String?,
        `interface`: String?,
        names: CValuesRef<CPointerVar<ByteVar>>?
    ): Int

    fun sd_bus_emit_object_added(bus: CValuesRef<sd_bus>?, path: String?): Int
    fun sd_bus_emit_object_removed(bus: CValuesRef<sd_bus>?, path: String?): Int
    fun sd_bus_emit_interfaces_added_strv(
        bus: CValuesRef<sd_bus>?,
        path: String?,
        interfaces: CValuesRef<CPointerVar<ByteVar>>?
    ): Int

    fun sd_bus_emit_interfaces_removed_strv(
        bus: CValuesRef<sd_bus>?,
        path: String?,
        interfaces: CValuesRef<CPointerVar<ByteVar>>?
    ): Int

    fun sd_bus_open(ret: CValuesRef<CPointerVar<sd_bus>>?): Int
    fun sd_bus_open_system(ret: CValuesRef<CPointerVar<sd_bus>>?): Int
    fun sd_bus_open_user(ret: CValuesRef<CPointerVar<sd_bus>>?): Int
    fun sd_bus_open_user_with_address(ret: CValuesRef<CPointerVar<sd_bus>>?, address: String): Int
    fun sd_bus_open_system_remote(ret: CValuesRef<CPointerVar<sd_bus>>?, host: String?): Int
    fun sd_bus_open_direct(ret: CValuesRef<CPointerVar<sd_bus>>?, address: String): Int
    fun sd_bus_open_direct(ret: CValuesRef<CPointerVar<sd_bus>>?, fd: Int): Int
    fun sd_bus_open_server(ret: CValuesRef<CPointerVar<sd_bus>>?, fd: Int): Int
    fun sd_bus_request_name(bus: CValuesRef<sd_bus>?, name: String?, flags: uint64_t): Int
    fun sd_bus_release_name(bus: CValuesRef<sd_bus>?, name: String?): Int
    fun sd_bus_get_unique_name(
        bus: CValuesRef<sd_bus>?,
        unique: CValuesRef<CPointerVar<ByteVar>>?
    ): Int

    fun sd_bus_add_object_vtable(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        path: String?,
        `interface`: String?,
        vtable: CValuesRef<sd_bus_vtable>?,
        userdata: CValuesRef<*>?
    ): Int

    fun sd_bus_add_object_manager(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        path: String?
    ): Int

    fun sd_bus_add_match(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        match: String?,
        callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?
    ): Int

    fun sd_bus_add_match_async(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        match: String?,
        callback: sd_bus_message_handler_t?,
        install_callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?
    ): Int

    fun sd_bus_match_signal(
        bus: CValuesRef<sd_bus>?,
        ret: CValuesRef<CPointerVar<sd_bus_slot>>?,
        sender: String?,
        path: String?,
        `interface`: String?,
        member: String?,
        callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?
    ): Int

    fun sd_bus_slot_unref(slot: CValuesRef<sd_bus_slot>?): CPointer<sd_bus_slot>?

    fun sd_bus_new(ret: CValuesRef<CPointerVar<sd_bus>>?): Int
    fun sd_bus_start(bus: CValuesRef<sd_bus>?): Int

    fun sd_bus_process(bus: CValuesRef<sd_bus>?, r: CValuesRef<CPointerVar<sd_bus_message>>?): Int
    fun sd_bus_get_current_message(bus: CValuesRef<sd_bus>?): CPointer<sd_bus_message>?

    fun sd_bus_get_poll_data(bus: CValuesRef<sd_bus>?, data: PollData): Int
    fun sd_bus_get_n_queued_read(bus: CValuesRef<sd_bus>?, ret: CValuesRef<uint64_tVar>?): Int
    fun sd_bus_flush(bus: CValuesRef<sd_bus>?): Int
    fun sd_bus_flush_close_unref(bus: CValuesRef<sd_bus>?): CPointer<sd_bus>?
    fun sd_bus_close_unref(bus: CValuesRef<sd_bus>?): CPointer<sd_bus>?

    fun sd_bus_message_set_destination(m: CValuesRef<sd_bus_message>?, destination: String?): Int

    fun sd_bus_query_sender_creds(
        m: CValuesRef<sd_bus_message>?,
        mask: uint64_t,
        creds: CValuesRef<CPointerVar<sd_bus_creds>>?
    ): Int

    fun sd_bus_creds_unref(c: CValuesRef<sd_bus_creds>?): CPointer<sd_bus_creds>?

    fun sd_bus_creds_get_pid(c: CValuesRef<sd_bus_creds>?, pid: CValuesRef<pid_tVar>?): Int
    fun sd_bus_creds_get_uid(c: CValuesRef<sd_bus_creds>?, uid: CValuesRef<uid_tVar>?): Int
    fun sd_bus_creds_get_euid(c: CValuesRef<sd_bus_creds>?, euid: CValuesRef<uid_tVar>?): Int
    fun sd_bus_creds_get_gid(c: CValuesRef<sd_bus_creds>?, gid: CValuesRef<gid_tVar>?): Int
    fun sd_bus_creds_get_egid(c: CValuesRef<sd_bus_creds>?, egid: CValuesRef<gid_tVar>?): Int
    fun sd_bus_creds_get_supplementary_gids(
        c: CValuesRef<sd_bus_creds>?,
        gids: CValuesRef<CPointerVar<gid_tVar>>?
    ): Int

    fun sd_bus_creds_get_selinux_context(
        c: CValuesRef<sd_bus_creds>?,
        context: CValuesRef<CPointerVar<ByteVar>>?
    ): Int
}
