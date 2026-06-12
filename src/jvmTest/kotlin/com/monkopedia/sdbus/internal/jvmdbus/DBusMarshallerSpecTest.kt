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
import kotlin.test.assertTrue

/**
 * Hand-computed spec canaries, round-trip property, and the #71/#74/#26/#11/#27 bug corpus for the
 * pure-Kotlin marshaller (epic #93, phase 1). These assert byte sequences against the D-Bus
 * specification directly (NOT mirrored from dbus-java) so the marshaller is spec-correct even where
 * dbus-java historically had quirks.
 */
class DBusMarshallerSpecTest {

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun bytesOf(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    private fun assertMarshals(
        signature: String,
        values: List<Any?>,
        expected: ByteArray,
        endian: Endian = Endian.LITTLE
    ) {
        val actual = DBusMarshaller.marshal(signature, values, endian)
        assertEquals(
            hex(expected),
            hex(actual),
            "marshal('$signature', $values, $endian)"
        )
    }

    // --- Spec canaries (hand-computed from the D-Bus specification) -------------------------

    @Test
    fun canary_byte() = assertMarshals("y", listOf(5.toUByte()), bytesOf(0x05))

    @Test
    fun canary_int32_littleEndian() =
        assertMarshals("i", listOf(42), bytesOf(0x2a, 0x00, 0x00, 0x00))

    @Test
    fun canary_int32_bigEndian() =
        assertMarshals("i", listOf(42), bytesOf(0x00, 0x00, 0x00, 0x2a), Endian.BIG)

    @Test
    fun canary_uint32() = assertMarshals(
        "u",
        listOf(0xDEADBEEFu),
        bytesOf(0xef, 0xbe, 0xad, 0xde)
    )

    @Test
    fun canary_boolean_true() = assertMarshals("b", listOf(true), bytesOf(0x01, 0x00, 0x00, 0x00))

    @Test
    fun canary_uint64_max() = assertMarshals(
        "t",
        listOf(ULong.MAX_VALUE),
        bytesOf(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)
    )

    @Test
    fun canary_double_one() = assertMarshals(
        "d",
        listOf(1.0),
        bytesOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xf0, 0x3f)
    )

    @Test
    fun canary_double_bigEndian() = assertMarshals(
        "d",
        listOf(1.0),
        bytesOf(0x3f, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        Endian.BIG
    )

    @Test
    fun canary_string() = assertMarshals(
        "s",
        listOf("foo"),
        // uint32 length 3, "foo", NUL
        bytesOf(0x03, 0x00, 0x00, 0x00, 0x66, 0x6f, 0x6f, 0x00)
    )

    @Test
    fun canary_signature() = assertMarshals(
        "g",
        // single-byte length 4, "a{sv}" is 5 chars... use "a{sv}" -> length 5
        listOf("a{sv}"),
        bytesOf(0x05, 0x61, 0x7b, 0x73, 0x76, 0x7d, 0x00)
    )

    @Test
    fun canary_array_of_bytes() = assertMarshals(
        "ay",
        listOf(listOf<UByte>(1u, 2u, 3u)),
        // uint32 length 3, then 1 2 3
        bytesOf(0x03, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03)
    )

    @Test
    fun canary_array_of_int32() = assertMarshals(
        "ai",
        listOf(listOf(1, 2)),
        // uint32 length 8 (content only), then two int32
        bytesOf(0x08, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00)
    )

    @Test
    fun canary_struct_is() = assertMarshals(
        "(is)",
        listOf(Message.JvmStructPayload("(is)", listOf(1, "a"))),
        // i=1, then s "a"
        bytesOf(0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x61, 0x00)
    )

    @Test
    fun canary_variant_int() = assertMarshals(
        "v",
        listOf(Message.JvmVariantPayload("i", 42)),
        // g: len 1 'i' NUL, pad to 4, int32 42
        bytesOf(0x01, 0x69, 0x00, 0x00, 0x2a, 0x00, 0x00, 0x00)
    )

    @Test
    fun canary_array_of_uint64_alignmentPadding() = assertMarshals(
        // uint32 length, then padding to 8 before the first element
        "at",
        listOf(listOf(1uL)),
        bytesOf(
            0x08, 0x00, 0x00, 0x00, // length 8
            0x00, 0x00, 0x00, 0x00, // pad to 8
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // element 1
        )
    )

    @Test
    fun canary_multipleTopLevel_struct74() = assertMarshals(
        // #74 grouped/multi-out: three top-level values stream with running alignment
        "isi",
        listOf(7, "hi", 9),
        bytesOf(
            0x07, 0x00, 0x00, 0x00, // i = 7
            0x02, 0x00, 0x00, 0x00, 0x68, 0x69, 0x00, // s = "hi" (len2 + bytes + NUL)
            0x00, // pad to 4 for next int32
            0x09, 0x00, 0x00, 0x00 // i = 9
        )
    )

    @Test
    fun canary_dict_sv_singleEntry() {
        val value = linkedMapOf<Any?, Any?>("k" to Message.JvmVariantPayload("i", 5))
        // a{sv}: len, (entry: align8) key s "k", value v: g 'i' + int
        // entry content: s "k" = 01 00 00 00 6b 00 (6 bytes), pad to ... value variant
        // g len1 'i' NUL = 01 69 00, pad to 4 (1 byte), int 5 = 05 00 00 00
        // entry bytes: 01 00 00 00 6b 00 | 01 69 00 00 | 05 00 00 00 => let length be computed
        val bytes = DBusMarshaller.marshal("a{sv}", listOf(value), Endian.LITTLE)
        // content length: entry(align8 from offset 8): key s "k" = 6 bytes, variant g 'i' = 3
        // bytes, pad-to-4 = 1 byte, int32 = 4 bytes => 16 (0x10) bytes of content.
        assertEquals(0x10, bytes[0].toInt() and 0xff, "a{sv} content length")
        val round = DBusMarshaller.unmarshal("a{sv}", bytes, 0, Endian.LITTLE)

        @Suppress("UNCHECKED_CAST")
        val map = round.values[0] as Map<Any?, Any?>
        assertEquals(setOf("k"), map.keys)
        assertEquals(5, (map["k"] as Message.JvmVariantPayload).value)
    }

    // --- Empty collections (#26 / #11) ------------------------------------------------------

    @Test
    fun emptyArray_as() =
        assertMarshals("as", listOf(emptyList<String>()), bytesOf(0x00, 0x00, 0x00, 0x00))

    @Test
    fun emptyArray_ai() =
        assertMarshals("ai", listOf(emptyList<Int>()), bytesOf(0x00, 0x00, 0x00, 0x00))

    @Test
    fun emptyArray_ay() =
        assertMarshals("ay", listOf(emptyList<UByte>()), bytesOf(0x00, 0x00, 0x00, 0x00))

    @Test
    fun emptyDict_asv_hasElementAlignmentPadding() = assertMarshals(
        // empty a{sv}: 4-byte length 0 + padding to the 8-byte dict-entry boundary
        "a{sv}",
        listOf(emptyMap<String, Any?>()),
        bytesOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    )

    @Test
    fun emptyCollections_roundTrip() {
        roundTripBoth("as", listOf(emptyList<String>()))
        roundTripBoth("ai", listOf(emptyList<Int>()))
        roundTripBoth("ay", listOf(emptyList<UByte>()))
        roundTripBoth("a{sv}", listOf(emptyMap<String, Any?>()))
        roundTripBoth("a{ss}", listOf(emptyMap<String, String>()))
        roundTripBoth("a(is)", listOf(emptyList<Message.JvmStructPayload>()))
    }

    // --- Unsigned round-trips across signed boundaries (#27) --------------------------------

    @Test
    fun unsigned_roundTrip_acrossSignedBoundaries() {
        roundTripBoth("y", listOf(UByte.MIN_VALUE))
        roundTripBoth("y", listOf(UByte.MAX_VALUE))
        roundTripBoth("y", listOf(200.toUByte()))

        roundTripBoth("q", listOf(UShort.MIN_VALUE))
        roundTripBoth("q", listOf(UShort.MAX_VALUE))
        roundTripBoth("q", listOf(40000.toUShort()))

        roundTripBoth("u", listOf(UInt.MIN_VALUE))
        roundTripBoth("u", listOf(UInt.MAX_VALUE))
        roundTripBoth("u", listOf(3_000_000_000u))

        roundTripBoth("t", listOf(ULong.MIN_VALUE))
        roundTripBoth("t", listOf(ULong.MAX_VALUE))
        roundTripBoth("t", listOf(18_000_000_000_000_000_000uL))
    }

    @Test
    fun signed_negativeValues_roundTrip() {
        roundTripBoth("n", listOf(Short.MIN_VALUE))
        roundTripBoth("n", listOf((-12345).toShort()))
        roundTripBoth("i", listOf(Int.MIN_VALUE))
        roundTripBoth("i", listOf(-2_000_000_000))
        roundTripBoth("x", listOf(Long.MIN_VALUE))
        roundTripBoth("x", listOf(-9_000_000_000_000_000_000L))
    }

    // --- Structs both directions (#71) ------------------------------------------------------

    @Test
    fun struct71_roundTrip() {
        roundTripBoth("(is)", listOf(Message.JvmStructPayload("(is)", listOf(99, "ninety-nine"))))
        roundTripBoth(
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
        roundTripBoth(
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
    fun wideStruct_roundTrip() {
        val wide = Message.JvmStructPayload(
            "(ybnqiuxtdsog)",
            listOf(
                7.toUByte(),
                true,
                (-100).toShort(),
                60000.toUShort(),
                -1_000_000,
                3_000_000_000u,
                -5_000_000_000L,
                ULong.MAX_VALUE,
                1.25,
                "wide",
                "/wide/struct",
                "(qd)"
            )
        )
        roundTripBoth("(ybnqiuxtdsog)", listOf(wide))
    }

    // --- Nested containers / dbusmock-style shapes ------------------------------------------

    @Test
    fun nestedDict_managedObjectsShape_roundTrip() {
        val nested = linkedMapOf<Any?, Any?>(
            "/dev/0" to linkedMapOf<Any?, Any?>(
                "org.example.Iface" to linkedMapOf<Any?, Any?>(
                    "Enabled" to Message.JvmVariantPayload("b", true),
                    "Level" to Message.JvmVariantPayload("i", 5)
                )
            ),
            "/dev/1" to linkedMapOf<Any?, Any?>(
                "org.example.Other" to linkedMapOf<Any?, Any?>()
            )
        )
        roundTripBoth("a{oa{sa{sv}}}", listOf(nested))
    }

    @Test
    fun arrayOfArrays_roundTrip() {
        roundTripBoth("aai", listOf(listOf(listOf(1, 2), emptyList(), listOf(3))))
    }

    @Test
    fun arrayOfVariants_roundTrip() {
        roundTripBoth(
            "av",
            listOf(
                listOf(
                    Message.JvmVariantPayload("i", 13),
                    Message.JvmVariantPayload("s", "two"),
                    Message.JvmVariantPayload("ai", listOf(9))
                )
            )
        )
    }

    @Test
    fun variantContainingStruct_roundTrip() {
        roundTripBoth(
            "v",
            listOf(
                Message.JvmVariantPayload(
                    "(is)",
                    Message.JvmStructPayload("(is)", listOf(5, "five"))
                )
            )
        )
    }

    @Test
    fun stringEdgeCases_roundTrip() {
        roundTripBoth("s", listOf(""))
        roundTripBoth("s", listOf("Hello, D-Bus éàü 你好 🚀"))
        roundTripBoth("s", listOf("line1\nline2\ttab \"quoted\" back\\slash"))
        roundTripBoth("g", listOf(""))
        roundTripBoth("g", listOf("(ybnqiuxtdsogv)"))
        roundTripBoth("o", listOf("/"))
        roundTripBoth("o", listOf("/com/monkopedia/sdbus/some/object"))
    }

    @Test
    fun doubleEdgeCases_roundTrip() {
        roundTripBoth("d", listOf(0.0))
        roundTripBoth("d", listOf(-0.0))
        roundTripBoth("d", listOf(Double.NaN))
        roundTripBoth("d", listOf(Double.POSITIVE_INFINITY))
        roundTripBoth("d", listOf(Double.NEGATIVE_INFINITY))
        roundTripBoth("d", listOf(1.0E300))
        roundTripBoth("d", listOf(1.0E-300))
    }

    @Test
    fun largeArray_roundTrip() {
        val big = List(128 * 1024) { (it % 256).toUByte() }
        roundTrip("ay", listOf(big), Endian.LITTLE)
    }

    // --- Round-trip helpers -----------------------------------------------------------------

    private fun roundTripBoth(signature: String, values: List<Any?>) {
        roundTrip(signature, values, Endian.LITTLE)
        roundTrip(signature, values, Endian.BIG)
    }

    private fun roundTrip(signature: String, values: List<Any?>, endian: Endian) {
        val bytes = DBusMarshaller.marshal(signature, values, endian)
        val result = DBusMarshaller.unmarshal(signature, bytes, 0, endian)
        assertEquals(bytes.size, result.offset, "unmarshal must consume all bytes for '$signature'")
        assertValuesEqual(values, result.values, "round-trip '$signature' ($endian)")
    }

    private fun assertValuesEqual(expected: List<Any?>, actual: List<Any?>, message: String) {
        assertEquals(expected.size, actual.size, "$message: value count")
        for (i in expected.indices) {
            assertValueEqual(expected[i], actual[i], "$message[$i]")
        }
    }

    private fun assertValueEqual(expected: Any?, actual: Any?, message: String) {
        when (expected) {
            is Double -> assertEquals(
                java.lang.Double.doubleToRawLongBits(expected),
                java.lang.Double.doubleToRawLongBits(actual as Double),
                "$message (double bits)"
            )

            is Map<*, *> -> {
                actual as Map<*, *>
                assertEquals(expected.keys.toList(), actual.keys.toList(), "$message: dict keys")
                for (k in expected.keys) {
                    assertValueEqual(expected[k], actual[k], "$message[$k]")
                }
            }

            is List<*> -> {
                actual as List<*>
                assertEquals(expected.size, actual.size, "$message: list size")
                for (i in expected.indices) {
                    assertValueEqual(expected[i], actual[i], "$message[$i]")
                }
            }

            is Message.JvmStructPayload -> {
                actual as Message.JvmStructPayload
                assertValuesEqual(expected.fields, actual.fields, "$message: struct fields")
            }

            is Message.JvmVariantPayload -> {
                actual as Message.JvmVariantPayload
                assertEquals(expected.signature, actual.signature, "$message: variant sig")
                assertValueEqual(expected.value, actual.value, "$message: variant value")
            }

            else -> assertEquals(expected, actual, message)
        }
    }

    @Test
    fun bigEndianRead_handCrafted() {
        // A hand-crafted big-endian body the writer never produced, to exercise the BE read path:
        // "si" = string "x" + int32 0x01020304
        val be = bytesOf(
            0x00, 0x00, 0x00, 0x01, 0x78, 0x00, // s: len 1 (BE) + 'x' + NUL
            0x00, 0x00, // pad to 4
            0x01, 0x02, 0x03, 0x04 // i: 0x01020304
        )
        val result = DBusMarshaller.unmarshal("si", be, 0, Endian.BIG)
        assertEquals("x", result.values[0])
        assertEquals(0x01020304, result.values[1])
        assertEquals(be.size, result.offset)
    }

    @Test
    fun offsetThreading_unmarshalReturnsConsumedOffset() {
        val bytes = DBusMarshaller.marshal("i", listOf(42), Endian.LITTLE) +
            DBusMarshaller.marshal("i", listOf(7), Endian.LITTLE)
        val first = DBusMarshaller.unmarshal("i", bytes, 0, Endian.LITTLE)
        assertEquals(42, first.values[0])
        assertEquals(4, first.offset)
        val second = DBusMarshaller.unmarshal("i", bytes, first.offset, Endian.LITTLE)
        assertEquals(7, second.values[0])
    }

    @Test
    fun signatureParser_handlesNesting() {
        assertTrue(DBusSignatureParser.parse("a{oa{sa{sv}}}").single() is DBusType.ArrayType)
        assertEquals(3, DBusSignatureParser.parse("isi").size)
        assertEquals("(ii)", DBusSignatureParser.parse("(ii)").single().code)
        assertEquals("aai", DBusSignatureParser.parse("aai").single().code)
    }
}
