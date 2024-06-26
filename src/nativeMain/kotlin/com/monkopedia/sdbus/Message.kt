@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus_creds
import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.ISdBus
import com.monkopedia.sdbus.internal.MessageDecoder
import com.monkopedia.sdbus.internal.MessageEncoder
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.internal.NativePtr.Companion.NULL
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer
import platform.posix.size_t
import platform.posix.size_tVar
import sdbus.SD_BUS_CREDS_AUGMENT
import sdbus.SD_BUS_CREDS_EGID
import sdbus.SD_BUS_CREDS_EUID
import sdbus.SD_BUS_CREDS_GID
import sdbus.SD_BUS_CREDS_PID
import sdbus.SD_BUS_CREDS_SELINUX_CONTEXT
import sdbus.SD_BUS_CREDS_SUPPLEMENTARY_GIDS
import sdbus.SD_BUS_CREDS_UID
import sdbus.SD_BUS_TYPE_ARRAY
import sdbus.SD_BUS_TYPE_BOOLEAN
import sdbus.SD_BUS_TYPE_BYTE
import sdbus.SD_BUS_TYPE_DICT_ENTRY
import sdbus.SD_BUS_TYPE_DOUBLE
import sdbus.SD_BUS_TYPE_INT16
import sdbus.SD_BUS_TYPE_INT32
import sdbus.SD_BUS_TYPE_INT64
import sdbus.SD_BUS_TYPE_OBJECT_PATH
import sdbus.SD_BUS_TYPE_SIGNATURE
import sdbus.SD_BUS_TYPE_STRING
import sdbus.SD_BUS_TYPE_STRUCT
import sdbus.SD_BUS_TYPE_UINT16
import sdbus.SD_BUS_TYPE_UINT32
import sdbus.SD_BUS_TYPE_UINT64
import sdbus.SD_BUS_TYPE_UNIX_FD
import sdbus.SD_BUS_TYPE_VARIANT
import sdbus.gid_t
import sdbus.gid_tVar
import sdbus.int16_t
import sdbus.int32_t
import sdbus.int64_t
import sdbus.pid_t
import sdbus.sd_bus_message_append_array
import sdbus.sd_bus_message_append_basic
import sdbus.sd_bus_message_at_end
import sdbus.sd_bus_message_close_container
import sdbus.sd_bus_message_copy
import sdbus.sd_bus_message_enter_container
import sdbus.sd_bus_message_exit_container
import sdbus.sd_bus_message_get_destination
import sdbus.sd_bus_message_get_interface
import sdbus.sd_bus_message_get_member
import sdbus.sd_bus_message_get_path
import sdbus.sd_bus_message_get_sender
import sdbus.sd_bus_message_is_empty
import sdbus.sd_bus_message_open_container
import sdbus.sd_bus_message_peek_type
import sdbus.sd_bus_message_read_array
import sdbus.sd_bus_message_read_basic
import sdbus.sd_bus_message_rewind
import sdbus.sd_bus_message_seal
import sdbus.uid_t
import sdbus.uint16_t
import sdbus.uint32_t
import sdbus.uint64_t
import sdbus.uint8_t

private inline fun debugPrint(msg: () -> String) {
    if (false) println(msg())
}

/********************************************/
/**
 * @class Message
 *
 * Message represents a D-Bus message, which can be either method call message,
 * method reply message, signal message, or a plain message.
 *
 * Serialization and deserialization functions are provided for types supported
 * by D-Bus.
 *
 * You mostly don't need to work with this class directly if you use high-level
 * APIs of @c IObject and @c IProxy.
 *
 ***********************************************/
open class Message internal constructor(
    protected val msg: CPointer<sd_bus_message>?,
    internal val sdbus: ISdBus,
    adoptMessage: Boolean = false
) {
    private var isOk: Boolean = true
    private val resource = msg to sdbus
    private val cleaner = createCleaner(resource) { (msg, sdbus) ->
        if (msg != null) {
            sdbus.sd_bus_message_unref(msg)
        }
    }

    init {
        if (!adoptMessage) {
            msg?.let {
                this.sdbus.sd_bus_message_ref(msg)
            }
        }
    }

    internal constructor(sdbus: ISdBus) : this(null, sdbus)

    constructor (o: Message) : this(o.msg, o.sdbus)

    internal fun append(item: Boolean): Unit = memScoped {
        debugPrint { "append boolean $item" }
        val itemP = cValuesOf(if (item) 1 else 0).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_BOOLEAN.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a bool value", -r)
    }

    internal fun append(item: Short): Unit = memScoped {
        debugPrint { "append short $item" }
        val itemP = cValuesOf(item.convert<int16_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_INT16.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a int16_t value", -r)
    }

    internal fun append(item: Int): Unit = memScoped {
        debugPrint { "append int $item" }
        val itemP = cValuesOf(item.convert<int32_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_INT32.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a int32_t value", -r)
    }

    internal fun append(item: Long): Unit = memScoped {
        debugPrint { "append long $item" }
        val itemP = cValuesOf(item.convert<int64_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_INT64.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a int64_t value", -r)
    }

    internal fun append(item: UByte): Unit = memScoped {
        debugPrint { "append ubyte $item" }
        val itemP = cValuesOf(item.convert<uint8_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_BYTE.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a byte value", -r)
    }

    internal fun append(item: UShort): Unit = memScoped {
        debugPrint { "append ushort $item" }
        val itemP = cValuesOf(item.convert<uint16_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_UINT16.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a uint16_t value", -r)
    }

    internal fun append(item: UInt): Unit = memScoped {
        debugPrint { "append uint $item" }
        val itemP = cValuesOf(item.convert<uint32_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_UINT32.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a uint32_t value", -r)
    }

    internal fun append(item: ULong): Unit = memScoped {
        debugPrint { "append ulong $item" }
        val itemP = cValuesOf(item.convert<uint64_t>()).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_UINT64.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a uint64_t value", -r)
    }

    internal fun append(item: Double): Unit = memScoped {
        debugPrint { "append double $item" }
        val itemP = cValuesOf(item).getPointer(this)

        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_DOUBLE.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize a double value", -r)
    }

    internal fun append(item: String) {
        debugPrint { "append string $item" }
        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_STRING.convert(), item.cstr)
        sdbusRequire(r < 0, "Failed to serialize a string value", -r)
    }

    internal fun appendObjectPath(item: String) {
        debugPrint { "append object path $item" }
        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_OBJECT_PATH.convert(), item.cstr)
        sdbusRequire(r < 0, "Failed to serialize an ObjectPath value", -r)
    }

    internal fun append(item: ObjectPath) {
        debugPrint { "append object path $item" }
        val r =
            sd_bus_message_append_basic(msg, SD_BUS_TYPE_OBJECT_PATH.convert(), item.value.cstr)
        sdbusRequire(r < 0, "Failed to serialize an ObjectPath value", -r)
    }

    internal fun append(item: Signature) {
        debugPrint { "append signature $item" }
        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_SIGNATURE.convert(), item.value.cstr)
        sdbusRequire(r < 0, "Failed to serialize an Signature value", -r)
    }

    internal fun appendSignature(item: String) {
        debugPrint { "append signature $item" }
        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_SIGNATURE.convert(), item.cstr)
        sdbusRequire(r < 0, "Failed to serialize an Signature value", -r)
    }

    internal fun append(item: UnixFd): Unit = memScoped {
        debugPrint { "append unix fd $item" }
        val itemP = cValuesOf(item.fd).getPointer(this)
        val r = sd_bus_message_append_basic(msg, SD_BUS_TYPE_UNIX_FD.convert(), itemP)
        sdbusRequire(r < 0, "Failed to serialize an UnixFd value", -r)
    }

    internal fun readBoolean(): Boolean = memScoped {
        debugPrint { "readBoolean" }
        val intItem = cValuesOf(0).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_BOOLEAN.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a bool value", -r)

        return intItem[0] != 0
    }

    internal fun readShort(): Short = memScoped {
        debugPrint { "readShort" }
        val intItem = cValuesOf(0.convert<int16_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_INT16.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a int16 value", -r)

        return intItem[0]
    }

    internal fun readInt(): Int = memScoped {
        debugPrint { "readInt" }
        val intItem = cValuesOf(0.convert<int32_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_INT32.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a int32 value", -r)

        return intItem[0]
    }

    internal fun readLong(): Long = memScoped {
        debugPrint { "readLong" }
        val intItem = cValuesOf(0.convert<int64_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_INT64.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a int64 value", -r)

        return intItem[0]
    }

    internal fun readUByte(): UByte = memScoped {
        debugPrint { "readUByte" }
        val intItem = cValuesOf(0.convert<uint8_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_BYTE.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a byte value", -r)

        return intItem[0]
    }

    internal fun readUShort(): UShort = memScoped {
        debugPrint { "readUShort" }
        val intItem = cValuesOf(0.convert<uint16_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_UINT16.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a uint16 value", -r)

        return intItem[0]
    }

    internal fun readUInt(): UInt = memScoped {
        debugPrint { "readUInt" }
        val intItem = cValuesOf(0.convert<uint32_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_UINT32.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a uint32 value", -r)

        return intItem[0]
    }

    internal fun readULong(): ULong = memScoped {
        debugPrint { "readULong" }
        val intItem = cValuesOf(0.convert<uint64_t>()).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_UINT64.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a uint64 value", -r)

        return intItem[0]
    }

    internal fun readDouble(): Double = memScoped {
        debugPrint { "readDouble" }
        val intItem = cValuesOf(0.0).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_DOUBLE.convert(), intItem)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize a double value", -r)

        return intItem[0]
    }

    internal fun readString(): String = memScoped {
        debugPrint { "readString" }
        val intItem = cValuesOf(interpretCPointer<ByteVar>(NULL)).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_STRING.convert(), intItem)
        if (r == 0) {
            isOk = false
            return ""
        }

        sdbusRequire(r < 0, "Failed to deserialize a string value", -r)

        return intItem[0]?.toKString()!!
    }

    internal fun readObjectPath(): ObjectPath = memScoped {
        debugPrint { "readObjectPath" }
        val intItem = cValuesOf(interpretCPointer<ByteVar>(NULL)).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_OBJECT_PATH.convert(), intItem)
        if (r == 0) {
            isOk = false
            return ObjectPath("")
        }

        sdbusRequire(r < 0, "Failed to deserialize a ObjectPath value", -r)

        return ObjectPath(intItem[0]?.toKString()!!)
    }

    internal fun readSignature(): Signature = memScoped {
        debugPrint { "readSignature" }
        val intItem = cValuesOf(interpretCPointer<ByteVar>(NULL)).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_SIGNATURE.convert(), intItem)
        if (r == 0) {
            isOk = false
            return Signature("")
        }

        sdbusRequire(r < 0, "Failed to deserialize a Signature value", -r)

        return Signature(intItem[0]?.toKString()!!)
    }

    internal fun readUnixFd(): UnixFd = memScoped {
        debugPrint { "readUnixFd" }
        val intItem = cValuesOf(0).getPointer(this)
        val r = sd_bus_message_read_basic(msg, SD_BUS_TYPE_UNIX_FD.convert(), intItem)
        if (r == 0) {
            isOk = false
            return UnixFd(0)
        }

        sdbusRequire(r < 0, "Failed to deserialize a bool value", -r)

        return UnixFd(intItem[0])
    }

    internal fun readVariant(): Variant = Variant(this).also {
        debugPrint { "readVariant" }
        it.deserializeFrom(this)
        if (it.isEmpty) {
            isOk = false
        }
    }

    internal fun openContainer(signature: String) {
        debugPrint { "Open container $msg $signature ${SD_BUS_TYPE_ARRAY}" }
        val r = sd_bus_message_open_container(msg, SD_BUS_TYPE_ARRAY.convert(), signature)
        sdbusRequire(r < 0, "Failed to open a container", -r)
    }

    internal fun closeContainer() {
        debugPrint { "Close container" }
        val r = sd_bus_message_close_container(msg)
        sdbusRequire(r < 0, "Failed to close a container", -r)
    }

    internal fun openDictEntry(signature: String) {
        debugPrint { "Open dict entry $msg $signature ${SD_BUS_TYPE_DICT_ENTRY}" }
        val r = sd_bus_message_open_container(msg, SD_BUS_TYPE_DICT_ENTRY.convert(), signature)
        sdbusRequire(r < 0, "Failed to open a dictionary entry", -r)
    }

    internal fun closeDictEntry() {
        debugPrint { "Close dict entry" }
        val r = sd_bus_message_close_container(msg)
        sdbusRequire(r < 0, "Failed to close a dictionary entry", -r)
    }

    internal fun openVariant(signature: String) {
        debugPrint { "Open variant $signature" }
        val r = sd_bus_message_open_container(msg, SD_BUS_TYPE_VARIANT.convert(), signature)
        sdbusRequire(r < 0, "Failed to open a variant", -r)
    }

    internal fun closeVariant() {
        debugPrint { "Close variant" }
        val r = sd_bus_message_close_container(msg)
        sdbusRequire(r < 0, "Failed to close a variant", -r)
    }

    internal fun openStruct(signature: String) {
        debugPrint { "Open struct $signature" }
        val r = sd_bus_message_open_container(msg, SD_BUS_TYPE_STRUCT.convert(), signature)
        sdbusRequire(r < 0, "Failed to open a struct", -r)
    }

    internal fun closeStruct() {
        debugPrint { "Close struct" }
        val r = sd_bus_message_close_container(msg)
        sdbusRequire(r < 0, "Failed to close a struct", -r)
    }

    internal fun enterContainer(signature: String) {
        debugPrint { "Enter container $signature ${peekType()}" }
        val r = sd_bus_message_enter_container(msg, SD_BUS_TYPE_ARRAY.convert(), signature)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to enter a container", -r)
    }

    internal fun exitContainer() {
        debugPrint { "Exit container" }
        val r = sd_bus_message_exit_container(msg)
        sdbusRequire(r < 0, "Failed to exit a container", -r)
    }

    internal fun enterDictEntry(signature: String) {
        debugPrint { "Enter dict entry $signature" }
        val r = sd_bus_message_enter_container(msg, SD_BUS_TYPE_DICT_ENTRY.convert(), signature)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to enter a dictionary entry", -r)
    }

    internal fun exitDictEntry() {
        debugPrint { "Exit dict entry" }
        val r = sd_bus_message_exit_container(msg)
        sdbusRequire(r < 0, "Failed to exit a dictionary entry", -r)
    }

    internal fun enterVariant(signature: String) {
        debugPrint { "Peek: ${peekType()}" }
        debugPrint { "Enter variant $signature" }
        val r = sd_bus_message_enter_container(msg, SD_BUS_TYPE_VARIANT.convert(), signature)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to enter a variant", -r)
    }

    internal fun exitVariant() {
        debugPrint { "Exit variant" }
        val r = sd_bus_message_exit_container(msg)
        sdbusRequire(r < 0, "Failed to exit a variant", -r)
    }

    internal fun enterStruct(signature: String) {
        debugPrint { "Enter struct $signature" }
        val r = sd_bus_message_enter_container(msg, SD_BUS_TYPE_STRUCT.convert(), signature)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to enter a struct", -r)
    }

    internal fun exitStruct() {
        debugPrint { "Exit struct" }
        val r = sd_bus_message_exit_container(msg)
        sdbusRequire(r < 0, "Failed to exit a struct", -r)
    }

    internal fun appendArray(type: Char, ptr: CPointer<*>, size: size_t) {
        debugPrint { "append array $type $size" }
        val r = sd_bus_message_append_array(msg, type.code.toByte(), ptr, size)
        sdbusRequire(r < 0, "Failed to serialize an array", -r)
    }

    internal fun readArray(
        type: Char,
        ptr: CValuesRef<COpaquePointerVar>,
        size: CValuesRef<size_tVar>
    ) {
        debugPrint { "Read array $type $size" }
        val r = sd_bus_message_read_array(msg, type.code.toByte(), ptr, size)
        if (r == 0) {
            isOk = false
        }

        sdbusRequire(r < 0, "Failed to deserialize an array", -r)
    }

    internal operator fun invoke(): Boolean = isOk
    internal fun clearFlags() {
        isOk = true
    }

    fun getInterfaceName(): String? = sd_bus_message_get_interface(msg)?.toKString()

    fun getMemberName(): String? = sd_bus_message_get_member(msg)?.toKString()

    fun getSender(): String? = sd_bus_message_get_sender(msg)?.toKString()

    fun getPath(): String? = sd_bus_message_get_path(msg)?.toKString()

    fun getDestination(): String? = sd_bus_message_get_destination(msg)?.toKString()

    fun peekType(): Pair<Char?, String?> = memScoped {
        val typeSignature = cValuesOf(0.toByte()).getPointer(this)
        val contentsSignature = cValuesOf(interpretCPointer<ByteVar>(NULL)).getPointer(this)
        val r: Int = sd_bus_message_peek_type(msg, typeSignature, contentsSignature)
        sdbusRequire(r < 0, "Failed to peek message type", -r)
        val type = typeSignature[0].toInt().toChar().takeIf { r > 0 }

        return type to contentsSignature[0]?.toKString()
    }

    val isValid: Boolean
        get() = msg != null
    val isEmpty: Boolean
        get() = sd_bus_message_is_empty(msg) != 0
    fun isAtEnd(complete: Boolean): Boolean = sd_bus_message_at_end(msg, if (complete) 1 else 0) > 0

    fun copyTo(destination: Message, complete: Boolean) {
        val r = sd_bus_message_copy(destination.msg, msg, if (complete) 1 else 0)
        sdbusRequire(r < 0, "Failed to copy the message", -r)
    }

    fun seal() {
        val messageCookie = 1
        val sealTimeout = 0
        val r = sd_bus_message_seal(msg, messageCookie.convert(), sealTimeout.convert())
        sdbusRequire(r < 0, "Failed to seal the message", -r)
    }

    fun rewind(complete: Boolean) {
        val r = sd_bus_message_rewind(msg, if (complete) 1 else 0)
        sdbusRequire(r < 0, "Failed to rewind the message", -r)
    }

    fun getCredsPid(): pid_t = memScoped {
        val mask = SD_BUS_CREDS_PID or SD_BUS_CREDS_AUGMENT
        val creds = getCreds(mask)

        val pid = cValuesOf(0.convert<pid_t>()).getPointer(this)
        val r = sdbus.sd_bus_creds_get_pid(creds[0], pid)
        sdbusRequire(r < 0, "Failed to get bus cred pid", -r)
        return pid[0]
    }

    fun getCredsUid(): uid_t = memScoped {
        val mask = SD_BUS_CREDS_UID or SD_BUS_CREDS_AUGMENT
        val creds = getCreds(mask)

        val uid = cValuesOf((-1).convert<uid_t>()).getPointer(this)
        val r = sdbus.sd_bus_creds_get_uid(creds[0], uid)
        sdbusRequire(r < 0, "Failed to get bus cred uid", -r)
        return uid[0]
    }

    fun getCredsEuid(): uid_t = memScoped {
        val mask = SD_BUS_CREDS_EUID or SD_BUS_CREDS_AUGMENT
        val creds = getCreds(mask)

        val euid = cValuesOf((-1).convert<uid_t>()).getPointer(this)
        val r = sdbus.sd_bus_creds_get_euid(creds[0], euid)
        sdbusRequire(r < 0, "Failed to get bus cred euid", -r)
        return euid[0]
    }

    fun getCredsGid(): gid_t = memScoped {
        val mask = SD_BUS_CREDS_GID or SD_BUS_CREDS_AUGMENT
        val creds = getCreds(mask)

        val gid = cValuesOf((-1).convert<gid_t>()).getPointer(this)
        val r = sdbus.sd_bus_creds_get_gid(creds[0], gid)
        sdbusRequire(r < 0, "Failed to get bus cred gid", -r)
        return gid[0]
    }

    fun getCredsEgid(): gid_t = memScoped {
        val mask = SD_BUS_CREDS_EGID or SD_BUS_CREDS_AUGMENT
        val creds = getCreds(mask)

        val egid = cValuesOf((-1).convert<gid_t>()).getPointer(this)
        val r = sdbus.sd_bus_creds_get_egid(creds[0], egid)
        sdbusRequire(r < 0, "Failed to get bus cred egid", -r)
        return egid[0]
    }

    fun getCredsSupplementaryGids(): List<gid_t> = memScoped {
        val mask = SD_BUS_CREDS_SUPPLEMENTARY_GIDS or SD_BUS_CREDS_AUGMENT
        val creds = getCreds(mask)

        val cGids = cValuesOf(interpretCPointer<gid_tVar>(NULL)).getPointer(this)
        val r = sdbus.sd_bus_creds_get_supplementary_gids(creds[0], cGids)
        sdbusRequire(r < 0, "Failed to get bus cred supplementary gids", -r)

        return List(r) {
            cGids[0]!![it]
        }
    }

    fun getSELinuxContext(): String = memScoped {
        val mask = SD_BUS_CREDS_AUGMENT or SD_BUS_CREDS_SELINUX_CONTEXT
        val creds = getCreds(mask)

        val cLabel = cValuesOf(interpretCPointer<ByteVar>(NULL)).getPointer(this)
        val r = sdbus.sd_bus_creds_get_selinux_context(creds[0], cLabel)
        sdbusRequire(r < 0, "Failed to get bus cred selinux context", -r)
        return cLabel[0]?.toKString()!!
    }

    private fun MemScope.getCreds(mask: ULong): CPointer<CPointerVar<sd_bus_creds>> {
        val creds = cValuesOf(interpretCPointer<sd_bus_creds>(NULL)).getPointer(this)
        defer {
            sdbus.sd_bus_creds_unref(creds[0])
        }

        val r = sdbus.sd_bus_query_sender_creds(msg, mask, creds)
        sdbusRequire(r < 0, "Failed to get bus creds", -r)
        return creds
    }
}

inline fun <reified T : Any> Message.serialize(arg: T) {
    serialize(serializer<T>(), serializersModuleOf(serializer<T>()), arg)
}

fun <T> Message.serialize(serializer: SerializationStrategy<T>, module: SerializersModule, arg: T) {
    MessageEncoder(this, module).encodeSerializableValue(serializer, arg)
}

inline fun <reified T : Any> Message.deserialize(): T =
    deserialize(serializer<T>(), serializersModuleOf(serializer<T>()))

fun <T : Any> Message.deserialize(
    serializer: DeserializationStrategy<T>,
    module: SerializersModule
): T = MessageDecoder(this, module).decodeSerializableValue(serializer)

internal fun Message.append(variant: Variant) {
    variant.serializeTo(this)
}

internal inline fun <T> Message.deserializeArrayFast(
    signature: SdbusSig,
    items: MutableList<T>
) = memScoped {
    val arrayPtr = cValue<COpaquePointerVar>().getPointer(this)
    val arraySize = cValuesOf(0.convert<size_t>()).getPointer(this)

    @Suppress("UNCHECKED_CAST")
    val converter = (signature as PrimitiveSig<T, CVariable>).converter!!
    readArray(signature.value[0], arrayPtr, arraySize)

    val count = arraySize[0] / converter.size.toUInt()
    converter.readNativeInto(arrayPtr[0]?.reinterpret()!!, count.convert(), items)
}

internal fun TypedArguments.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST")
    val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.serialize(
    types: List<KSerializer<*>>,
    args: List<Any>,
    module: SerializersModule
) {
    for ((s, a) in types.zip(args)) {
        @Suppress("UNCHECKED_CAST")
        serialize(s as KSerializer<Any>, module, a)
    }
}

internal fun Message.serialize(typedArgs: TypedArguments) {
    val types = typedArgs.inputType.map { it.type }
    val args = typedArgs.values
    val module = typedArgs.module()
    serialize(types, args, module)
}

internal fun TypedMethod.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST")
    val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.deserialize(
    types: List<KSerializer<*>>,
    module: SerializersModule
): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return types.map { deserialize(it as KSerializer<Any>, module) }
}

internal fun Message.deserialize(typedArgs: TypedMethod): List<Any> {
    val types = typedArgs.inputType.map { it.type }
    val module = typedArgs.module()
    return deserialize(types, module)
}

internal fun <T : Any> Message.serialize(outputType: Typed<T>, result: T) {
    val module = serializersModuleOf(outputType.cls, outputType.type)
    serialize(outputType.type, module, result)
}
