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
import kotlin.test.Test
import kotlin.test.assertEquals
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.UInt64
import org.freedesktop.dbus.types.Variant as JVariant

/**
 * Differential tests against dbus-java 5.2.0 used as a marshalling oracle (epic #93, phase 1).
 *
 * For a broad value matrix, this marshals with OUR marshaller and with dbus-java's internal
 * `Message.append(signature, values...)` body marshaller and asserts BYTE-IDENTICAL output, in
 * both endiannesses. It also cross-demarshals: dbus-java's reference bytes are fed back through
 * OUR reader to confirm our read path matches dbus-java's write path.
 *
 * dbus-java's body marshaller is reached by reflection on a bare [org.freedesktop.dbus.messages.Message]
 * (protected no-arg ctor + `updateEndianess` + protected `append`); since `bytecounter` starts at
 * 0 the produced wire chunks are body-relative aligned, which is exactly what our marshaller emits.
 */
class DBusMarshallerDifferentialTest {

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    // Unsigned literals in a heterogeneous listOf(...) can be inferred to a wider unsigned type
    // (e.g. ULong); accept any unsigned/integral boxing here.
    private fun unsignedLong(value: Any?): Long = when (value) {
        is UByte -> value.toLong()
        is UShort -> value.toLong()
        is UInt -> value.toLong()
        is ULong -> value.toLong()
        is Number -> value.toLong()
        else -> error("Expected unsigned/integral, got ${value?.javaClass}")
    }

    /** Marshals [values] under [signature] with dbus-java's internal body marshaller. */
    private fun dbusJavaBody(signature: String, values: List<Any?>, endian: Endian): ByteArray {
        val cls = Class.forName("org.freedesktop.dbus.messages.Message")
        val ctor = cls.getDeclaredConstructor().apply { isAccessible = true }
        val msg = ctor.newInstance()
        // Marshalling endianness is the private `big` field (updateEndianess only writes the
        // header flag byte, not the marshalling direction).
        cls.getDeclaredField("big").apply { isAccessible = true }
            .setBoolean(msg, endian == Endian.BIG)
        val append = cls.getDeclaredMethod("append", String::class.java, Array<Any?>::class.java)
            .apply { isAccessible = true }
        val types = DBusSignatureParser.parse(signature)
        val args = values.mapIndexed { i, v -> toDbusJava(types[i], v) }.toTypedArray()
        append.invoke(msg, signature, args)
        @Suppress("UNCHECKED_CAST")
        val chunks = cls.getMethod("getWireData").invoke(msg) as Array<ByteArray?>
        var out = ByteArray(0)
        for (chunk in chunks) {
            if (chunk != null) out += chunk
        }
        return out
    }

    /** Adapts a JVM value-model value into the Java type dbus-java's `append` expects. */
    private fun toDbusJava(type: DBusType, value: Any?): Any? = when (type) {
        is DBusType.Basic -> when (type.type) {
            'y' -> (value as UByte).toByte()
            'b' -> value as Boolean
            'n' -> value as Short
            'q' -> UInt16((unsignedLong(value) and 0xffff).toInt())
            'i' -> value as Int
            'u' -> UInt32(unsignedLong(value) and 0xffffffffL)
            'x' -> value as Long
            't' -> UInt64(java.lang.Long.toUnsignedString(unsignedLong(value)))
            'd' -> value as Double
            's' -> value as String
            'o' -> DBusPath(value as String)
            'g' -> value as String
            else -> value
        }

        is DBusType.ArrayType -> when (val element = type.element) {
            is DBusType.DictEntryType -> {
                val map = value as Map<*, *>
                val out = LinkedHashMap<Any?, Any?>()
                for ((k, v) in map) {
                    out[toDbusJava(element.key, k)] = toDbusJava(element.value, v)
                }
                out
            }

            is DBusType.Basic -> if (element.type == 'y') {
                val list = value as List<*>
                ByteArray(list.size) { (list[it] as UByte).toByte() }
            } else {
                (value as List<*>).map { toDbusJava(element, it) }.toTypedArray()
            }

            else -> (value as List<*>).map { toDbusJava(element, it) }.toTypedArray()
        }

        is DBusType.StructType -> {
            val fields = (value as Message.JvmStructPayload).fields
            Array(fields.size) { toDbusJava(type.fields[it], fields[it]) }
        }

        is DBusType.DictEntryType -> value
        DBusType.VariantType -> {
            val payload = value as Message.JvmVariantPayload
            val inner = DBusSignatureParser.parseOne(payload.signature, 0).first
            JVariant(toDbusJava(inner, payload.value), payload.signature)
        }
    }

    private fun assertByteIdentical(signature: String, values: List<Any?>) {
        for (endian in listOf(Endian.LITTLE, Endian.BIG)) {
            val ours = DBusMarshaller.marshal(signature, values, endian)
            val reference = dbusJavaBody(signature, values, endian)
            assertEquals(
                hex(reference),
                hex(ours),
                "byte-identical marshal of '$signature' ($endian)"
            )
            // Cross-demarshal: dbus-java reference bytes -> our reader -> re-marshal must match.
            val decoded = DBusMarshaller.unmarshal(signature, reference, 0, endian)
            assertEquals(reference.size, decoded.offset, "consumed all of '$signature' ($endian)")
            val reEncoded = DBusMarshaller.marshal(signature, decoded.values, endian)
            assertEquals(
                hex(reference),
                hex(reEncoded),
                "re-marshal of our demarshalled dbus-java bytes for '$signature' ($endian)"
            )
        }
    }

    @Test
    fun basicTypes_matchDbusJava() {
        assertByteIdentical("y", listOf(0.toUByte()))
        assertByteIdentical("y", listOf(UByte.MAX_VALUE))
        assertByteIdentical("b", listOf(true))
        assertByteIdentical("b", listOf(false))
        assertByteIdentical("n", listOf(Short.MIN_VALUE))
        assertByteIdentical("n", listOf(Short.MAX_VALUE))
        assertByteIdentical("q", listOf(UShort.MAX_VALUE))
        assertByteIdentical("i", listOf(Int.MIN_VALUE))
        assertByteIdentical("i", listOf(0x01020304))
        assertByteIdentical("u", listOf(UInt.MAX_VALUE))
        assertByteIdentical("u", listOf(0xDEADBEEFu))
        assertByteIdentical("x", listOf(Long.MIN_VALUE))
        assertByteIdentical("x", listOf(Long.MAX_VALUE))
        assertByteIdentical("t", listOf(ULong.MAX_VALUE))
        assertByteIdentical("t", listOf(0uL))
        assertByteIdentical("d", listOf(3.141592653589793))
        assertByteIdentical("d", listOf(-2.5))
    }

    @Test
    fun stringLikeTypes_matchDbusJava() {
        assertByteIdentical("s", listOf(""))
        assertByteIdentical("s", listOf("Hello, D-Bus éàü 你好 🚀"))
        assertByteIdentical("o", listOf("/com/example/Foo"))
        assertByteIdentical("g", listOf("a{sv}"))
        assertByteIdentical("g", listOf("(ybnqiuxtdsogv)"))
    }

    @Test
    fun multipleTopLevel_matchDbusJava() {
        assertByteIdentical("isi", listOf(7, "hi", 9))
        assertByteIdentical("iis", listOf(-7, 42, "tail"))
        assertByteIdentical("tu", listOf(9_000_000_000_000_000_000uL, 4_000_000_000u))
    }

    @Test
    fun arrays_matchDbusJava() {
        assertByteIdentical("ay", listOf(listOf<UByte>(0u, 1u, 127u, 128u, 255u)))
        assertByteIdentical("ai", listOf(listOf(1, 2, 3, -4, Int.MAX_VALUE, Int.MIN_VALUE)))
        assertByteIdentical("as", listOf(listOf("a", "", "c with spaces", "déjà-vu")))
        assertByteIdentical("ad", listOf(listOf(1.5, -2.5, 0.0, 1.0E300)))
        assertByteIdentical("at", listOf(listOf(0uL, 1uL, ULong.MAX_VALUE)))
        assertByteIdentical("ab", listOf(listOf(true, false, true)))
        assertByteIdentical("ao", listOf(listOf("/a", "/a/b")))
        assertByteIdentical("aai", listOf(listOf(listOf(1, 2), emptyList(), listOf(3))))
    }

    @Test
    fun emptyArrays_matchDbusJava() {
        assertByteIdentical("ai", listOf(emptyList<Int>()))
        assertByteIdentical("as", listOf(emptyList<String>()))
        assertByteIdentical("ay", listOf(emptyList<UByte>()))
        assertByteIdentical("at", listOf(emptyList<ULong>()))
    }

    @Test
    fun dicts_matchDbusJava() {
        assertByteIdentical(
            "a{ss}",
            listOf(linkedMapOf<Any?, Any?>("one" to "1", "two" to "2", "" to "empty-key"))
        )
        assertByteIdentical(
            "a{ix}",
            listOf(linkedMapOf<Any?, Any?>(1 to 10L, -2 to Long.MIN_VALUE))
        )
        assertByteIdentical("a{ss}", listOf(emptyMap<Any?, Any?>()))
    }

    @Test
    fun structs_matchDbusJava() {
        assertByteIdentical(
            "(is)",
            listOf(Message.JvmStructPayload("(is)", listOf(99, "ninety-nine")))
        )
        assertByteIdentical(
            "((is)ai)",
            listOf(
                Message.JvmStructPayload(
                    "((is)ai)",
                    listOf(
                        Message.JvmStructPayload("(is)", listOf(1, "a")),
                        listOf(7, 8, 9)
                    )
                )
            )
        )
        assertByteIdentical(
            "a(is)",
            listOf(
                listOf(
                    Message.JvmStructPayload("(is)", listOf(1, "one")),
                    Message.JvmStructPayload("(is)", listOf(2, "two"))
                )
            )
        )
    }

    @Test
    fun variants_matchDbusJava() {
        assertByteIdentical("v", listOf(Message.JvmVariantPayload("i", 7)))
        assertByteIdentical("v", listOf(Message.JvmVariantPayload("s", "inside")))
        assertByteIdentical("v", listOf(Message.JvmVariantPayload("t", ULong.MAX_VALUE)))
        assertByteIdentical("v", listOf(Message.JvmVariantPayload("ai", listOf(1, 2, 3))))
    }

    @Test
    fun dictOfStringVariant_matchDbusJava() {
        assertByteIdentical(
            "a{sv}",
            listOf(
                linkedMapOf<Any?, Any?>(
                    "count" to Message.JvmVariantPayload("i", 7),
                    "name" to Message.JvmVariantPayload("s", "widget")
                )
            )
        )
    }
}
