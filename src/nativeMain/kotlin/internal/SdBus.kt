@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_creds
import cnames.structs.sd_bus_message
import cnames.structs.sd_bus_slot
import header.ISdBus
import header.ISdBus.PollData
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import sdbus.gid_tVar
import sdbus.pid_tVar
import sdbus.sd_bus_error
import sdbus.sd_bus_get_events
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_vtable
import sdbus.sd_id128_t
import sdbus.uid_tVar
import sdbus.uint64_t
import sdbus.uint64_tVar
import sdbus.uint8_t

internal class SdBus : ISdBus {
    private val lock = ReentrantLock()

    override fun sd_bus_message_ref(m: CValuesRef<sd_bus_message>?): CPointer<sd_bus_message>? =
        lock.withLock {
            return sdbus.sd_bus_message_ref(m)
        }

    override fun sd_bus_message_unref(m: CValuesRef<sd_bus_message>?): CPointer<sd_bus_message>? =
        lock.withLock {
            return sdbus.sd_bus_message_unref(m)
        }

    override fun sd_bus_send(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<sd_bus_message>?,
        cookie: CValuesRef<uint64_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_send(bus, m, cookie).also {
            if (it >= 0) {
                sdbus.sd_bus_flush(bus ?: sdbus.sd_bus_message_get_bus(m))
            }
        }
    }

    override fun sd_bus_call(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<sd_bus_message>?,
        usec: uint64_t,
        ret_error: CValuesRef<sd_bus_error>?,
        reply: CValuesRef<CPointerVar<sd_bus_message>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_call(bus, m, usec, ret_error, reply)
    }

    override fun sd_bus_call_async(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        m: CValuesRef<sd_bus_message>?,
        callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?,
        usec: uint64_t
    ): Int = lock.withLock {
        return sdbus.sd_bus_call_async(bus, slot, m, callback, userdata, usec).also {
            if (it >= 0) {
                sdbus.sd_bus_flush(bus ?: sdbus.sd_bus_message_get_bus(m))
            }
        }
    }

    override fun sd_bus_message_new(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        type: uint8_t
    ): Int = lock.withLock {
        return sdbus.sd_bus_message_new(bus, m, type)
    }

    override fun sd_bus_message_new_method_call(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        destination: String?,
        path: String?,
        `interface`: String?,
        member: String?
    ): Int = lock.withLock {
        return sdbus.sd_bus_message_new_method_call(bus, m, destination, path, `interface`, member)
    }

    override fun sd_bus_message_new_signal(
        bus: CValuesRef<sd_bus>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        path: String?,
        `interface`: String?,
        member: String?
    ): Int = lock.withLock {
        return sdbus.sd_bus_message_new_signal(bus, m, path, `interface`, member)
    }

    override fun sd_bus_message_new_method_return(
        call: CValuesRef<sd_bus_message>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?
    ): Int {
        return sdbus.sd_bus_message_new_method_return(call, m)
    }

    override fun sd_bus_message_new_method_error(
        call: CValuesRef<sd_bus_message>?,
        m: CValuesRef<CPointerVar<sd_bus_message>>?,
        e: CValuesRef<sd_bus_error>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_message_new_method_error(call, m, e)
    }

    override fun sd_bus_set_method_call_timeout(bus: CValuesRef<sd_bus>?, usec: uint64_t): Int =
        lock.withLock {
            return sdbus.sd_bus_set_method_call_timeout(bus, usec)
        }

    override fun sd_bus_get_method_call_timeout(
        bus: CValuesRef<sd_bus>?,
        ret: CValuesRef<uint64_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_get_method_call_timeout(bus, ret)
    }

    override fun sd_bus_emit_properties_changed_strv(
        bus: CValuesRef<sd_bus>?,
        path: String?,
        `interface`: String?,
        names: CValuesRef<CPointerVar<ByteVar>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_emit_properties_changed_strv(bus, path, `interface`, names)
    }

    override fun sd_bus_emit_object_added(bus: CValuesRef<sd_bus>?, path: String?): Int =
        lock.withLock {
            return sdbus.sd_bus_emit_object_added(bus, path)
        }

    override fun sd_bus_emit_object_removed(bus: CValuesRef<sd_bus>?, path: String?): Int =
        lock.withLock {
            return sdbus.sd_bus_emit_object_removed(bus, path)
        }

    override fun sd_bus_emit_interfaces_added_strv(
        bus: CValuesRef<sd_bus>?,
        path: String?,
        interfaces: CValuesRef<CPointerVar<ByteVar>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_emit_interfaces_added_strv(bus, path, interfaces)
    }

    override fun sd_bus_emit_interfaces_removed_strv(
        bus: CValuesRef<sd_bus>?,
        path: String?,
        interfaces: CValuesRef<CPointerVar<ByteVar>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_emit_interfaces_removed_strv(bus, path, interfaces)
    }

    override fun sd_bus_open(ret: CValuesRef<CPointerVar<sd_bus>>?): Int {
        return sdbus.sd_bus_open(ret)
    }

    override fun sd_bus_open_system(ret: CValuesRef<CPointerVar<sd_bus>>?): Int {
        return sdbus.sd_bus_open_system(ret)
    }

    override fun sd_bus_open_user(ret: CValuesRef<CPointerVar<sd_bus>>?): Int {
        return sdbus.sd_bus_open_user(ret)
    }

    override fun sd_bus_open_user_with_address(
        ret: CValuesRef<CPointerVar<sd_bus>>?,
        address: String
    ): Int = memScoped {
        val cValue = cValue<CPointerVar<sd_bus>>().getPointer(this)
        var r = sdbus.sd_bus_new(cValue)
        if (r < 0) return@memScoped r
        val bus = cValue[0]
        r = sdbus.sd_bus_set_address(bus, address)
        if (r < 0) return@memScoped r
        r = sdbus.sd_bus_set_bus_client(bus, 1)
        if (r < 0) return@memScoped r
        r = sdbus.sd_bus_set_trusted(bus, 1)
        if (r < 0) return@memScoped r
        r = sdbus.sd_bus_start(bus)
        if (r < 0) return@memScoped r

        ret?.getPointer(this)?.set(0, bus)

        0
    }

    override fun sd_bus_open_direct(ret: CValuesRef<CPointerVar<sd_bus>>?, address: String): Int =
        memScoped {
            val cValue = cValue<CPointerVar<sd_bus>>().getPointer(this)
            var r = sdbus.sd_bus_new(cValue)
            if (r < 0) {
                return@memScoped r
            }
            val bus = cValue[0]
            r = sdbus.sd_bus_set_address(bus, address)
            if (r < 0) {
                return@memScoped r
            }
            r = sdbus.sd_bus_start(bus)
            if (r < 0) {
                return@memScoped r
            }

            ret?.getPointer(this)?.set(0, bus)

            0
        }

    override fun sd_bus_open_direct(ret: CValuesRef<CPointerVar<sd_bus>>?, fd: Int): Int =
        memScoped {
            val cValue = cValue<CPointerVar<sd_bus>>().getPointer(this)
            var r = sdbus.sd_bus_new(cValue)
            if (r < 0) return@memScoped r
            val bus = cValue[0]
            r = sdbus.sd_bus_set_fd(bus, fd, fd)
            if (r < 0) return@memScoped r
            r = sdbus.sd_bus_start(bus)
            if (r < 0) return@memScoped r

            ret?.getPointer(this)?.set(0, bus)

            0
        }

    override fun sd_bus_open_server(ret: CValuesRef<CPointerVar<sd_bus>>?, fd: Int): Int =
        memScoped {
            val cValue = cValue<CPointerVar<sd_bus>>().getPointer(this)
            var r = sdbus.sd_bus_new(cValue)
            if (r < 0) return@memScoped r

            val bus = cValue[0]
            r = sdbus.sd_bus_set_fd(bus, fd, fd)
            if (r < 0) return@memScoped r

            val id: CValue<sd_id128_t> = cValue()
            r = sdbus.sd_id128_randomize(id)
            if (r < 0) return@memScoped r

            r = sdbus.sd_bus_set_server(bus, 1, id)
            if (r < 0) return@memScoped r

            r = sdbus.sd_bus_start(bus)
            if (r < 0) return@memScoped r

            ret?.getPointer(this)?.set(0, bus)

            0
        }

    override fun sd_bus_open_system_remote(
        ret: CValuesRef<CPointerVar<sd_bus>>?,
        host: String?
    ): Int {
        return sdbus.sd_bus_open_system_remote(ret, host)
    }

    override fun sd_bus_request_name(
        bus: CValuesRef<sd_bus>?,
        name: String?,
        flags: uint64_t
    ): Int = lock.withLock {
        return sdbus.sd_bus_request_name(bus, name, flags)
    }

    override fun sd_bus_release_name(bus: CValuesRef<sd_bus>?, name: String?): Int = lock.withLock {
        return sdbus.sd_bus_release_name(bus, name)
    }

    override fun sd_bus_get_unique_name(
        bus: CValuesRef<sd_bus>?,
        unique: CValuesRef<CPointerVar<ByteVar>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_get_unique_name(bus, unique)
    }

    override fun sd_bus_add_object_vtable(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        path: String?,
        `interface`: String?,
        vtable: CValuesRef<sd_bus_vtable>?,
        userdata: CValuesRef<*>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_add_object_vtable(bus, slot, path, `interface`, vtable, userdata)
    }

    override fun sd_bus_add_object_manager(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        path: String?
    ): Int = lock.withLock {
        return sdbus.sd_bus_add_object_manager(bus, slot, path)
    }

    override fun sd_bus_add_match(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        match: String?,
        callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_add_match(bus, slot, match, callback, userdata)
    }

    override fun sd_bus_add_match_async(
        bus: CValuesRef<sd_bus>?,
        slot: CValuesRef<CPointerVar<sd_bus_slot>>?,
        match: String?,
        callback: sd_bus_message_handler_t?,
        install_callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_add_match_async(bus, slot, match, callback, install_callback, userdata)
    }

    override fun sd_bus_match_signal(
        bus: CValuesRef<sd_bus>?,
        ret: CValuesRef<CPointerVar<sd_bus_slot>>?,
        sender: String?,
        path: String?,
        `interface`: String?,
        member: String?,
        callback: sd_bus_message_handler_t?,
        userdata: CValuesRef<*>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_match_signal(
            bus,
            ret,
            sender,
            path,
            `interface`,
            member,
            callback,
            userdata
        )
    }

    override fun sd_bus_slot_unref(slot: CValuesRef<sd_bus_slot>?): CPointer<sd_bus_slot>? =
        lock.withLock {
            return sdbus.sd_bus_slot_unref(slot)
        }

    override fun sd_bus_new(ret: CValuesRef<CPointerVar<sd_bus>>?): Int {
        return sdbus.sd_bus_new(ret)
    }

    override fun sd_bus_start(bus: CValuesRef<sd_bus>?): Int = lock.withLock {
        return sdbus.sd_bus_start(bus)
    }

    override fun sd_bus_process(
        bus: CValuesRef<sd_bus>?,
        r: CValuesRef<CPointerVar<sd_bus_message>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_process(bus, r)
    }

    override fun sd_bus_get_current_message(bus: CValuesRef<sd_bus>?): CPointer<sd_bus_message>? =
        sdbus.sd_bus_get_current_message(bus)

    override fun sd_bus_get_poll_data(bus: CValuesRef<sd_bus>?, data: PollData): Int =
        lock.withLock {
            var r = sdbus.sd_bus_get_fd(bus)
            if (r < 0) return r
            data.fd = r

            r = sd_bus_get_events(bus)
            if (r < 0) return r
            data.events = r.convert()

            memScoped {
                val out = cValue<uint64_tVar>().getPointer(this)
                r = sdbus.sd_bus_get_timeout(bus, out)
                data.timeout_usec = out[0]
            }
            r
        }

    override fun sd_bus_get_n_queued_read(
        bus: CValuesRef<sd_bus>?,
        ret: CValuesRef<uint64_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_get_n_queued_read(bus, ret)
    }

    override fun sd_bus_flush(bus: CValuesRef<sd_bus>?): Int {
        return sdbus.sd_bus_flush(bus)
    }

    override fun sd_bus_flush_close_unref(bus: CValuesRef<sd_bus>?): CPointer<sd_bus>? {
        return sdbus.sd_bus_flush_close_unref(bus)
    }

    override fun sd_bus_close_unref(bus: CValuesRef<sd_bus>?): CPointer<sd_bus>? {
        return sdbus.sd_bus_close_unref(bus)
    }

    override fun sd_bus_message_set_destination(
        m: CValuesRef<sd_bus_message>?,
        destination: String?
    ): Int = lock.withLock {
        return sdbus.sd_bus_message_set_destination(m, destination)
    }

    override fun sd_bus_query_sender_creds(
        m: CValuesRef<sd_bus_message>?,
        mask: uint64_t,
        creds: CValuesRef<CPointerVar<sd_bus_creds>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_query_sender_creds(m, mask, creds)
    }

    override fun sd_bus_creds_unref(c: CValuesRef<sd_bus_creds>?): CPointer<sd_bus_creds>? =
        lock.withLock {
            return sdbus.sd_bus_creds_unref(c)
        }

    override fun sd_bus_creds_get_pid(
        c: CValuesRef<sd_bus_creds>?,
        pid: CValuesRef<pid_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_pid(c, pid)
    }

    override fun sd_bus_creds_get_uid(
        c: CValuesRef<sd_bus_creds>?,
        uid: CValuesRef<uid_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_uid(c, uid)
    }

    override fun sd_bus_creds_get_euid(
        c: CValuesRef<sd_bus_creds>?,
        euid: CValuesRef<uid_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_euid(c, euid)
    }

    override fun sd_bus_creds_get_gid(
        c: CValuesRef<sd_bus_creds>?,
        gid: CValuesRef<gid_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_gid(c, gid)
    }

    override fun sd_bus_creds_get_egid(
        c: CValuesRef<sd_bus_creds>?,
        egid: CValuesRef<gid_tVar>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_egid(c, egid)
    }

    override fun sd_bus_creds_get_supplementary_gids(
        c: CValuesRef<sd_bus_creds>?,
        gids: CValuesRef<CPointerVar<gid_tVar>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_supplementary_gids(c, gids)
    }

    override fun sd_bus_creds_get_selinux_context(
        c: CValuesRef<sd_bus_creds>?,
        context: CValuesRef<CPointerVar<ByteVar>>?
    ): Int = lock.withLock {
        return sdbus.sd_bus_creds_get_selinux_context(c, context)
    }
}
