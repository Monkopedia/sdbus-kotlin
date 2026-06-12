/**
 *
 * (C) 2024 - 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.Message
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** The four D-Bus message types, by their wire code (the second byte of the fixed header). */
internal enum class WireMessageType(val code: Int) {
    METHOD_CALL(1),
    METHOD_RETURN(2),
    ERROR(3),
    SIGNAL(4);

    companion object {
        fun fromCode(code: Int): WireMessageType = entries.firstOrNull { it.code == code }
            ?: throw DBusMarshallingException("Unknown message type code: $code")
    }
}

/** D-Bus message header flags (a bit-field in the third byte of the fixed header). */
internal object WireMessageFlags {
    const val NO_REPLY_EXPECTED = 0x1
    const val NO_AUTO_START = 0x2
    const val ALLOW_INTERACTIVE_AUTHORIZATION = 0x4
}

/** The header-field codes carried by the `a(yv)` array of every message. */
internal object WireHeaderField {
    const val PATH = 1
    const val INTERFACE = 2
    const val MEMBER = 3
    const val ERROR_NAME = 4
    const val REPLY_SERIAL = 5
    const val DESTINATION = 6
    const val SENDER = 7
    const val SIGNATURE = 8
    const val UNIX_FDS = 9
}

/**
 * A single complete, low-level D-Bus message: the fixed header fields, the decoded header-field
 * array (path/interface/member/...), and the decoded [body] values.
 *
 * This is a dedicated wire-layer model for epic #93 phase 2 — independent of the high-level
 * `Message.jvm.kt` value model, though both share the same boxed value representation (Strings,
 * unsigned boxes, [Message.JvmStructPayload]/[Message.JvmVariantPayload]) so [DBusMarshaller] can
 * marshal the body directly.
 */
internal data class WireMessage(
    val type: WireMessageType,
    val flags: Int = 0,
    val serial: Int = 0,
    val path: String? = null,
    val interfaceName: String? = null,
    val member: String? = null,
    val errorName: String? = null,
    val replySerial: Int? = null,
    val destination: String? = null,
    val sender: String? = null,
    val signature: String? = null,
    val unixFds: Int? = null,
    val body: List<Any?> = emptyList()
) {
    val isReply: Boolean get() = type == WireMessageType.METHOD_RETURN ||
        type == WireMessageType.ERROR
}

/**
 * Reads and writes complete D-Bus messages over a byte stream, using [DBusMarshaller] for both the
 * header-fields array (`a(yv)`) and the body.
 *
 * Wire layout (per the D-Bus specification):
 * ```
 * byte  0      endianness ('l' / 'B')
 * byte  1      message type
 * byte  2      flags
 * byte  3      protocol version (1)
 * u32   4..7   body length
 * u32   8..11  serial
 * a(yv) 12..   header fields (4-byte length prefix, then 8-aligned structs)
 * <pad to 8-byte boundary>
 * body  ...    marshalled against the SIGNATURE header field
 * ```
 * Outgoing messages are always written little-endian; incoming messages honour their declared
 * endianness byte.
 */
internal object WireMessageCodec {
    private const val PROTOCOL_VERSION = 1

    // The full header up to (but excluding) the trailing pad+body, expressed as one signature so
    // the marshaller produces correct absolute alignment counted from the message start.
    private const val HEADER_SIGNATURE = "yyyyuua(yv)"

    /** Encodes [message] into a complete wire frame (little-endian). */
    fun encode(message: WireMessage): ByteArray {
        val endian = Endian.LITTLE
        val bodyBytes = if (message.signature.isNullOrEmpty() || message.body.isEmpty()) {
            ByteArray(0)
        } else {
            DBusMarshaller.marshal(message.signature, message.body, endian)
        }

        val fields = buildHeaderFields(message)
        val headerValues = listOf<Any?>(
            endian.code,
            message.type.code.toUByte(),
            message.flags.toUByte(),
            PROTOCOL_VERSION.toUByte(),
            bodyBytes.size.toUInt(),
            message.serial.toUInt(),
            fields
        )

        val writer = DBusWriter(endian)
        writer.marshal(DBusSignatureParser.parse(HEADER_SIGNATURE), headerValues)
        writer.align(8)
        val headerBytes = writer.toByteArray()

        return headerBytes + bodyBytes
    }

    /** Writes [message] to [out] and flushes. */
    fun write(out: OutputStream, message: WireMessage) {
        out.write(encode(message))
        out.flush()
    }

    private fun buildHeaderFields(message: WireMessage): List<Message.JvmStructPayload> {
        val fields = mutableListOf<Message.JvmStructPayload>()
        fun add(code: Int, variantSig: String, value: Any?) {
            fields.add(
                Message.JvmStructPayload(
                    "(yv)",
                    listOf(code.toUByte(), Message.JvmVariantPayload(variantSig, value))
                )
            )
        }
        message.path?.let { add(WireHeaderField.PATH, "o", it) }
        message.interfaceName?.let { add(WireHeaderField.INTERFACE, "s", it) }
        message.member?.let { add(WireHeaderField.MEMBER, "s", it) }
        message.errorName?.let { add(WireHeaderField.ERROR_NAME, "s", it) }
        message.replySerial?.let { add(WireHeaderField.REPLY_SERIAL, "u", it.toUInt()) }
        message.destination?.let { add(WireHeaderField.DESTINATION, "s", it) }
        message.sender?.let { add(WireHeaderField.SENDER, "s", it) }
        message.signature?.let { add(WireHeaderField.SIGNATURE, "g", it) }
        message.unixFds?.let { add(WireHeaderField.UNIX_FDS, "u", it.toUInt()) }
        return fields
    }

    /** Decodes a complete wire frame from [bytes] (for tests / round-trips). */
    fun decode(bytes: ByteArray): WireMessage {
        val endian = Endian.fromCode(bytes[0])
        return decodeFromFull(bytes, endian)
    }

    /** Blocks reading exactly one complete message from [input]. */
    fun read(input: InputStream): WireMessage {
        // The fixed header (12 bytes) plus the header-fields array length prefix (4 bytes) are
        // always present, so we can safely read 16 bytes to learn the remaining sizes.
        val prefix = readFully(input, 16)
        val endian = Endian.fromCode(prefix[0])
        val pre = DBusReader(prefix, 0, endian)
        // "yyyyuuu": endian, type, flags, version, bodyLength, serial, then the a(yv) length.
        val header = pre.unmarshal(DBusSignatureParser.parse("yyyyuuu"))
        val bodyLength = (header[4] as UInt).toInt()
        val fieldsLength = (header[6] as UInt).toInt()

        val headerEnd = 16 + fieldsLength
        val paddedHeaderEnd = align8(headerEnd)
        val remaining = readFully(input, (paddedHeaderEnd - 16) + bodyLength)

        val full = ByteArray(prefix.size + remaining.size)
        System.arraycopy(prefix, 0, full, 0, prefix.size)
        System.arraycopy(remaining, 0, full, prefix.size, remaining.size)
        return decodeFromFull(full, endian)
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeFromFull(full: ByteArray, endian: Endian): WireMessage {
        val reader = DBusReader(full, 0, endian)
        val header = reader.unmarshal(DBusSignatureParser.parse(HEADER_SIGNATURE))
        val type = WireMessageType.fromCode((header[1] as UByte).toInt())
        val flags = (header[2] as UByte).toInt()
        val serial = (header[5] as UInt).toInt()
        val fields = header[6] as List<Message.JvmStructPayload>

        var path: String? = null
        var interfaceName: String? = null
        var member: String? = null
        var errorName: String? = null
        var replySerial: Int? = null
        var destination: String? = null
        var sender: String? = null
        var signature: String? = null
        var unixFds: Int? = null
        for (field in fields) {
            val code = (field.fields[0] as UByte).toInt()
            val variant = field.fields[1] as Message.JvmVariantPayload
            when (code) {
                WireHeaderField.PATH -> path = variant.value as String
                WireHeaderField.INTERFACE -> interfaceName = variant.value as String
                WireHeaderField.MEMBER -> member = variant.value as String
                WireHeaderField.ERROR_NAME -> errorName = variant.value as String
                WireHeaderField.REPLY_SERIAL -> replySerial = (variant.value as UInt).toInt()
                WireHeaderField.DESTINATION -> destination = variant.value as String
                WireHeaderField.SENDER -> sender = variant.value as String
                WireHeaderField.SIGNATURE -> signature = variant.value as String
                WireHeaderField.UNIX_FDS -> unixFds = (variant.value as UInt).toInt()
            }
        }

        reader.align(8)
        val body = if (signature.isNullOrEmpty()) {
            emptyList()
        } else {
            reader.unmarshal(DBusSignatureParser.parse(signature))
        }

        return WireMessage(
            type = type,
            flags = flags,
            serial = serial,
            path = path,
            interfaceName = interfaceName,
            member = member,
            errorName = errorName,
            replySerial = replySerial,
            destination = destination,
            sender = sender,
            signature = signature,
            unixFds = unixFds,
            body = body
        )
    }

    private fun align8(value: Int): Int {
        val rem = value % 8
        return if (rem == 0) value else value + (8 - rem)
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) throw EOFException("End of stream after $offset of $count bytes")
            offset += read
        }
        return buffer
    }
}
