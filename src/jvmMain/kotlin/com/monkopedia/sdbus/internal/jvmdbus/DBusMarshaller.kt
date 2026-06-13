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
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd

/**
 * Pure-Kotlin byte-level D-Bus marshaller for the JVM backend (epic #93, phase 1).
 *
 * Converts BETWEEN the JVM value model used by `Message.jvm.kt` (boxed Boolean/Short/Int/Long/
 * Double, [UByte]/[UShort]/[UInt]/[ULong], String, [ObjectPath]/[Signature]/[UnixFd], [List] for
 * arrays, [Map] for dictionaries, [Message.JvmStructPayload] for structs, and
 * [Message.JvmVariantPayload] for variants) and D-Bus wire bytes, driven by a D-Bus type
 * signature.
 *
 * Implements the D-Bus marshalling rules faithfully: per-type natural alignment counted from the
 * start of the message body, all basic types, arrays/structs/dict-entries/variants, and BOTH
 * little- and big-endian read+write. This phase is standalone (no transport, no connection wiring)
 * and exercised by hand-computed spec canaries, real-peer round-trips, and the
 * #71/#74/#26/#11/#27 bug corpus.
 *
 * NOTE: variants are consumed/produced as [Message.JvmVariantPayload]; the
 * `com.monkopedia.sdbus.Variant` <-> [Message.JvmVariantPayload] conversion already exists in the
 * connection layer and will bridge in a later phase.
 */
internal enum class Endian(val code: Byte) {
    LITTLE('l'.code.toByte()),
    BIG('B'.code.toByte());

    companion object {
        fun fromCode(code: Byte): Endian = when (code.toInt().toChar()) {
            'l' -> LITTLE
            'B' -> BIG
            else -> throw DBusMarshallingException("Unknown endianness flag: $code")
        }
    }
}

internal class DBusMarshallingException(message: String) : RuntimeException(message)

/**
 * The parsed model of a single complete D-Bus type, carrying its natural [alignment] and the
 * signature fragment it [code]s back to.
 */
internal sealed class DBusType {
    abstract val alignment: Int
    abstract val code: String

    data class Basic(val type: Char) : DBusType() {
        override val alignment: Int get() = alignmentOfBasic(type)
        override val code: String get() = type.toString()
    }

    data class ArrayType(val element: DBusType) : DBusType() {
        override val alignment: Int get() = 4
        override val code: String get() = "a${element.code}"
    }

    data class StructType(val fields: List<DBusType>) : DBusType() {
        override val alignment: Int get() = 8
        override val code: String get() = "(${fields.joinToString("") { it.code }})"
    }

    data class DictEntryType(val key: DBusType, val value: DBusType) : DBusType() {
        override val alignment: Int get() = 8
        override val code: String get() = "{${key.code}${value.code}}"
    }

    data object VariantType : DBusType() {
        override val alignment: Int get() = 1
        override val code: String get() = "v"
    }

    companion object {
        fun alignmentOfBasic(type: Char): Int = when (type) {
            'y', 'g' -> 1
            'n', 'q' -> 2
            'b', 'i', 'u', 'h', 's', 'o' -> 4
            'x', 't', 'd' -> 8
            else -> throw DBusMarshallingException("Unknown basic type '$type'")
        }
    }
}

/**
 * Recursive-descent tokenizer for D-Bus type signatures. Handles nesting of `a`, `()`, `a{}`, `v`,
 * e.g. `(ii)`, `a{sv}`, `aai`, `a{oa{sa{sv}}}`.
 */
internal object DBusSignatureParser {
    /** Parses a whole signature into the ordered list of its top-level types. */
    fun parse(signature: String): List<DBusType> {
        val out = mutableListOf<DBusType>()
        var index = 0
        while (index < signature.length) {
            val (type, next) = parseOne(signature, index)
            out.add(type)
            index = next
        }
        return out
    }

    /** Parses exactly one complete type starting at [index]; returns it and the next index. */
    fun parseOne(signature: String, index: Int): Pair<DBusType, Int> {
        if (index >= signature.length) {
            throw DBusMarshallingException("Unexpected end of signature '$signature'")
        }
        return when (val c = signature[index]) {
            'a' -> {
                if (signature.getOrNull(index + 1) == '{') {
                    val (entry, next) = parseDictEntry(signature, index + 1)
                    DBusType.ArrayType(entry) to next
                } else {
                    val (element, next) = parseOne(signature, index + 1)
                    DBusType.ArrayType(element) to next
                }
            }

            '(' -> parseStruct(signature, index)
            '{' -> parseDictEntry(signature, index)
            'v' -> DBusType.VariantType to index + 1
            'y', 'b', 'n', 'q', 'i', 'u', 'x', 't', 'd', 's', 'o', 'g', 'h' ->
                DBusType.Basic(c) to index + 1

            else -> throw DBusMarshallingException(
                "Unknown signature char '$c' at $index in '$signature'"
            )
        }
    }

    private fun parseStruct(signature: String, openIndex: Int): Pair<DBusType, Int> {
        val fields = mutableListOf<DBusType>()
        var index = openIndex + 1
        while (signature.getOrNull(index) != ')') {
            if (index >= signature.length) {
                throw DBusMarshallingException("Unterminated struct in '$signature'")
            }
            val (field, next) = parseOne(signature, index)
            fields.add(field)
            index = next
        }
        if (fields.isEmpty()) {
            throw DBusMarshallingException("Empty struct in '$signature'")
        }
        return DBusType.StructType(fields) to index + 1
    }

    private fun parseDictEntry(signature: String, openIndex: Int): Pair<DBusType, Int> {
        val (key, afterKey) = parseOne(signature, openIndex + 1)
        val (value, afterValue) = parseOne(signature, afterKey)
        if (signature.getOrNull(afterValue) != '}') {
            throw DBusMarshallingException("Unterminated dict-entry in '$signature'")
        }
        return DBusType.DictEntryType(key, value) to afterValue + 1
    }
}

/**
 * Marshals JVM value-model values into D-Bus wire bytes. Alignment padding is written as NUL and
 * counted from the start of the buffer (which equals the start of the message body).
 */
internal class DBusWriter(private val endian: Endian) {
    private var buffer = ByteArray(64)
    var size: Int = 0
        private set

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var newSize = buffer.size * 2
        while (newSize < size + extra) newSize *= 2
        buffer = buffer.copyOf(newSize)
    }

    fun align(boundary: Int) {
        val rem = size % boundary
        if (rem == 0) return
        val pad = boundary - rem
        ensure(pad)
        repeat(pad) { buffer[size++] = 0 }
    }

    private fun putRaw(value: Long, bytes: Int) {
        ensure(bytes)
        when (endian) {
            Endian.LITTLE -> for (i in 0 until bytes) {
                buffer[size++] = ((value shr (8 * i)) and 0xff).toByte()
            }

            Endian.BIG -> for (i in 0 until bytes) {
                buffer[size++] = ((value shr (8 * (bytes - 1 - i))) and 0xff).toByte()
            }
        }
    }

    fun putByte(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    private fun putAligned(value: Long, bytes: Int) {
        align(bytes)
        putRaw(value, bytes)
    }

    private fun putUInt32At(position: Int, value: Long) {
        when (endian) {
            Endian.LITTLE -> for (i in 0 until 4) {
                buffer[position + i] = ((value shr (8 * i)) and 0xff).toByte()
            }

            Endian.BIG -> for (i in 0 until 4) {
                buffer[position + i] = ((value shr (8 * (3 - i))) and 0xff).toByte()
            }
        }
    }

    /** Marshals [values], one per top-level [types] entry, in order into the body. */
    fun marshal(types: List<DBusType>, values: List<Any?>) {
        if (types.size != values.size) {
            throw DBusMarshallingException(
                "Signature/value count mismatch: ${types.size} types vs ${values.size} values"
            )
        }
        for (i in types.indices) {
            marshalValue(types[i], values[i])
        }
    }

    fun marshalValue(type: DBusType, value: Any?) {
        when (type) {
            is DBusType.Basic -> marshalBasic(type.type, value)
            is DBusType.ArrayType -> marshalArray(type, value)
            is DBusType.StructType -> marshalStruct(type, value)
            is DBusType.DictEntryType -> marshalDictEntry(type, value)
            DBusType.VariantType -> marshalVariant(value)
        }
    }

    private fun marshalBasic(type: Char, value: Any?) {
        when (type) {
            'y' -> putByte(asLong(value).toInt())
            'b' -> putAligned(if (asBoolean(value)) 1L else 0L, 4)
            'n' -> putAligned(asLong(value) and 0xffff, 2)
            'q' -> putAligned(asLong(value) and 0xffff, 2)
            'i' -> putAligned(asLong(value) and 0xffffffffL, 4)
            'u', 'h' -> putAligned(asLong(value) and 0xffffffffL, 4)
            'x' -> putAligned(asLong(value), 8)
            't' -> putAligned(asLong(value), 8)
            'd' -> putAligned(java.lang.Double.doubleToRawLongBits(asDouble(value)), 8)
            's', 'o' -> marshalString(asString(value), lengthPrefixBytes = 4)
            'g' -> marshalString(asString(value), lengthPrefixBytes = 1)
            else -> throw DBusMarshallingException("Unknown basic type '$type'")
        }
    }

    private fun marshalString(value: String, lengthPrefixBytes: Int) {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        if (lengthPrefixBytes == 4) {
            putAligned(utf8.size.toLong() and 0xffffffffL, 4)
        } else {
            putByte(utf8.size)
        }
        ensure(utf8.size + 1)
        System.arraycopy(utf8, 0, buffer, size, utf8.size)
        size += utf8.size
        buffer[size++] = 0
    }

    private fun marshalArray(type: DBusType.ArrayType, value: Any?) {
        align(4)
        val lengthPosition = size
        putRaw(0L, 4) // placeholder for the content byte-length
        align(type.element.alignment)
        val contentStart = size
        when (val element = type.element) {
            is DBusType.DictEntryType -> {
                val map = asMap(value)
                for ((k, v) in map) {
                    marshalDictEntryFields(element, k, v)
                }
            }

            else -> {
                for (item in asIterable(value)) {
                    marshalValue(element, item)
                }
            }
        }
        putUInt32At(lengthPosition, (size - contentStart).toLong() and 0xffffffffL)
    }

    private fun marshalStruct(type: DBusType.StructType, value: Any?) {
        align(8)
        val fields = asStructFields(value)
        if (fields.size != type.fields.size) {
            throw DBusMarshallingException(
                "Struct field count mismatch for ${type.code}: expected " +
                    "${type.fields.size}, got ${fields.size}"
            )
        }
        for (i in type.fields.indices) {
            marshalValue(type.fields[i], fields[i])
        }
    }

    private fun marshalDictEntry(type: DBusType.DictEntryType, value: Any?) {
        val pair = value as? Pair<*, *>
            ?: throw DBusMarshallingException("Expected Pair for dict-entry ${type.code}")
        marshalDictEntryFields(type, pair.first, pair.second)
    }

    private fun marshalDictEntryFields(type: DBusType.DictEntryType, key: Any?, value: Any?) {
        align(8)
        marshalValue(type.key, key)
        marshalValue(type.value, value)
    }

    private fun marshalVariant(value: Any?) {
        val payload = value as? Message.JvmVariantPayload
            ?: throw DBusMarshallingException(
                "Expected Message.JvmVariantPayload for variant, got ${value?.javaClass}"
            )
        marshalString(payload.signature, lengthPrefixBytes = 1)
        val (type, end) = DBusSignatureParser.parseOne(payload.signature, 0)
        if (end != payload.signature.length) {
            throw DBusMarshallingException(
                "Variant signature must be a single complete type: '${payload.signature}'"
            )
        }
        marshalValue(type, payload.value)
    }
}

/**
 * Demarshals D-Bus wire bytes into JVM value-model values, driven by a signature. Alignment skips
 * padding bytes counted from the start of [bytes].
 */
internal class DBusReader(private val bytes: ByteArray, offset: Int, private val endian: Endian) {
    var offset: Int = offset
        private set

    fun align(boundary: Int) {
        val rem = offset % boundary
        if (rem != 0) offset += boundary - rem
    }

    private fun readRaw(count: Int): Long {
        if (offset + count > bytes.size) {
            throw DBusMarshallingException(
                "Out of bounds read: need $count at $offset, have ${bytes.size}"
            )
        }
        var result = 0L
        when (endian) {
            Endian.LITTLE -> for (i in 0 until count) {
                result = result or ((bytes[offset + i].toLong() and 0xff) shl (8 * i))
            }

            Endian.BIG -> for (i in 0 until count) {
                result = result or
                    ((bytes[offset + i].toLong() and 0xff) shl (8 * (count - 1 - i)))
            }
        }
        offset += count
        return result
    }

    private fun readByte(): Int {
        if (offset >= bytes.size) {
            throw DBusMarshallingException("Out of bounds read at $offset")
        }
        return bytes[offset++].toInt() and 0xff
    }

    /** Reads [types], one value per type, in order. */
    fun unmarshal(types: List<DBusType>): List<Any?> = types.map { unmarshalValue(it) }

    fun unmarshalValue(type: DBusType): Any? = when (type) {
        is DBusType.Basic -> unmarshalBasic(type.type)
        is DBusType.ArrayType -> unmarshalArray(type)
        is DBusType.StructType -> unmarshalStruct(type)
        is DBusType.DictEntryType -> unmarshalDictEntry(type)
        DBusType.VariantType -> unmarshalVariant()
    }

    private fun unmarshalBasic(type: Char): Any = when (type) {
        'y' -> readByte().toUByte()
        'b' -> {
            align(4)
            readRaw(4) != 0L
        }
        'n' -> {
            align(2)
            readRaw(2).toShort()
        }
        'q' -> {
            align(2)
            (readRaw(2).toInt() and 0xffff).toUShort()
        }
        'i' -> {
            align(4)
            readRaw(4).toInt()
        }
        'u' -> {
            align(4)
            readRaw(4).toUInt()
        }
        'h' -> {
            align(4)
            readRaw(4).toInt()
        }
        'x' -> {
            align(8)
            readRaw(8)
        }
        't' -> {
            align(8)
            readRaw(8).toULong()
        }
        'd' -> {
            align(8)
            java.lang.Double.longBitsToDouble(readRaw(8))
        }
        's', 'o' -> readString(lengthPrefixBytes = 4)
        'g' -> readString(lengthPrefixBytes = 1)
        else -> throw DBusMarshallingException("Unknown basic type '$type'")
    }

    private fun readString(lengthPrefixBytes: Int): String {
        val length = if (lengthPrefixBytes == 4) {
            align(4)
            readRaw(4).toInt()
        } else {
            readByte()
        }
        if (offset + length + 1 > bytes.size) {
            throw DBusMarshallingException("String out of bounds: len $length at $offset")
        }
        val result = String(bytes, offset, length, Charsets.UTF_8)
        offset += length + 1 // skip the trailing NUL
        return result
    }

    private fun unmarshalArray(type: DBusType.ArrayType): Any {
        align(4)
        val byteLength = readRaw(4).toInt()
        align(type.element.alignment)
        val end = offset + byteLength
        if (end > bytes.size) {
            throw DBusMarshallingException("Array out of bounds: len $byteLength at $offset")
        }
        return when (val element = type.element) {
            is DBusType.DictEntryType -> {
                val map = LinkedHashMap<Any?, Any?>()
                while (offset < end) {
                    align(8)
                    val key = unmarshalValue(element.key)
                    val value = unmarshalValue(element.value)
                    map[key] = value
                }
                map
            }

            else -> {
                val list = mutableListOf<Any?>()
                while (offset < end) {
                    list.add(unmarshalValue(element))
                }
                list
            }
        }
    }

    private fun unmarshalStruct(type: DBusType.StructType): Message.JvmStructPayload {
        align(8)
        val fields = type.fields.map { unmarshalValue(it) }
        return Message.JvmStructPayload(type.code, fields)
    }

    private fun unmarshalDictEntry(type: DBusType.DictEntryType): Pair<Any?, Any?> {
        align(8)
        val key = unmarshalValue(type.key)
        val value = unmarshalValue(type.value)
        return key to value
    }

    private fun unmarshalVariant(): Message.JvmVariantPayload {
        val signature = readString(lengthPrefixBytes = 1)
        val (type, end) = DBusSignatureParser.parseOne(signature, 0)
        if (end != signature.length) {
            throw DBusMarshallingException(
                "Variant signature must be a single complete type: '$signature'"
            )
        }
        return Message.JvmVariantPayload(signature, unmarshalValue(type))
    }
}

/**
 * Holds the result of a demarshalling pass: the decoded [values] plus the [offset] just past the
 * last byte consumed (so callers can chain reads, e.g. body after header).
 */
internal data class DBusUnmarshalResult(val values: List<Any?>, val offset: Int)

/** Entry points for the marshaller, mirroring the eventual connection-layer call sites. */
internal object DBusMarshaller {
    fun marshal(signature: String, values: List<Any?>, endian: Endian): ByteArray {
        val types = DBusSignatureParser.parse(signature)
        val writer = DBusWriter(endian)
        writer.marshal(types, values)
        return writer.toByteArray()
    }

    fun unmarshal(
        signature: String,
        bytes: ByteArray,
        offset: Int,
        endian: Endian
    ): DBusUnmarshalResult {
        val types = DBusSignatureParser.parse(signature)
        val reader = DBusReader(bytes, offset, endian)
        val values = reader.unmarshal(types)
        return DBusUnmarshalResult(values, reader.offset)
    }
}

// --- value-model coercion helpers -----------------------------------------------------------

private fun asLong(value: Any?): Long = when (value) {
    is UByte -> value.toLong()
    is UShort -> value.toLong()
    is UInt -> value.toLong()
    is ULong -> value.toLong()
    is Byte -> value.toLong()
    is Short -> value.toLong()
    is Int -> value.toLong()
    is Long -> value
    is UnixFd -> value.fd.toLong()
    is Number -> value.toLong()
    else -> throw DBusMarshallingException("Expected integral value, got ${value?.javaClass}")
}

private fun asBoolean(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    else -> throw DBusMarshallingException("Expected boolean, got ${value?.javaClass}")
}

private fun asDouble(value: Any?): Double = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Number -> value.toDouble()
    else -> throw DBusMarshallingException("Expected double, got ${value?.javaClass}")
}

private fun asString(value: Any?): String = when (value) {
    is String -> value
    is ObjectPath -> value.value
    is Signature -> value.value
    else -> throw DBusMarshallingException("Expected string-like, got ${value?.javaClass}")
}

private fun asIterable(value: Any?): Iterable<Any?> = when (value) {
    is List<*> -> value
    is Array<*> -> value.asList()
    is ByteArray -> value.asList().map { it.toUByte() }
    else -> throw DBusMarshallingException("Expected array-like, got ${value?.javaClass}")
}

private fun asMap(value: Any?): Map<*, *> = value as? Map<*, *>
    ?: throw DBusMarshallingException("Expected Map for dict, got ${value?.javaClass}")

private fun asStructFields(value: Any?): List<Any?> = when (value) {
    is Message.JvmStructPayload -> value.fields
    is List<*> -> value
    is Array<*> -> value.asList()
    else -> throw DBusMarshallingException("Expected struct payload, got ${value?.javaClass}")
}
