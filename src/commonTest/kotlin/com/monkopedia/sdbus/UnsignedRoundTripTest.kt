package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

/**
 * Systematic round-trip coverage for the unsigned inline-class types
 * (`UByte`/`UShort`/`UInt`/`ULong`, mapping to D-Bus `y`/`q`/`u`/`t`).
 *
 * The unsigned-as-inline-class mapping has produced [ClassCastException]s twice
 * (native `ListEncoder` boxing, JVM `ay`->`UByte` decode), so this exercises the
 * serialize/deserialize paths exhaustively: scalars, list elements, dict keys and
 * values, struct fields, and variant payloads. Each case round-trips through a
 * [PlainMessage] (which drives the same backend encoder/decoder used on the bus)
 * and asserts exact value equality so any sign-extension or truncation bug surfaces.
 *
 * Boundary values are covered for every type: 0, the type maximum, and a mid value.
 */
class UnsignedRoundTripTest {

    // --- Scalars -----------------------------------------------------------

    @Test
    fun uByte_scalar_roundTripsBoundaryValues() {
        for (value in uByteValues) {
            assertEquals(value, roundTrip(value))
        }
    }

    @Test
    fun uShort_scalar_roundTripsBoundaryValues() {
        for (value in uShortValues) {
            assertEquals(value, roundTrip(value))
        }
    }

    @Test
    fun uInt_scalar_roundTripsBoundaryValues() {
        for (value in uIntValues) {
            assertEquals(value, roundTrip(value))
        }
    }

    @Test
    fun uLong_scalar_roundTripsBoundaryValues() {
        for (value in uLongValues) {
            assertEquals(value, roundTrip(value))
        }
    }

    // --- Lists (arrays of unsigned) ---------------------------------------

    @Test
    fun uByte_list_roundTripsBoundaryValues() {
        assertEquals(uByteValues, roundTrip(uByteValues))
    }

    @Test
    fun uShort_list_roundTripsBoundaryValues() {
        assertEquals(uShortValues, roundTrip(uShortValues))
    }

    @Test
    fun uInt_list_roundTripsBoundaryValues() {
        assertEquals(uIntValues, roundTrip(uIntValues))
    }

    @Test
    fun uLong_list_roundTripsBoundaryValues() {
        assertEquals(uLongValues, roundTrip(uLongValues))
    }

    // --- Dicts valued by unsigned -----------------------------------------

    @Test
    fun uByte_asDictValue_roundTripsBoundaryValues() {
        val map = uByteValues.withIndex().associate { (i, v) -> i to v }
        assertEquals(map, roundTrip(map))
    }

    @Test
    fun uShort_asDictValue_roundTripsBoundaryValues() {
        val map = uShortValues.withIndex().associate { (i, v) -> i to v }
        assertEquals(map, roundTrip(map))
    }

    @Test
    fun uInt_asDictValue_roundTripsBoundaryValues() {
        val map = uIntValues.withIndex().associate { (i, v) -> i to v }
        assertEquals(map, roundTrip(map))
    }

    @Test
    fun uLong_asDictValue_roundTripsBoundaryValues() {
        val map = uLongValues.withIndex().associate { (i, v) -> i to v }
        assertEquals(map, roundTrip(map))
    }

    // --- Dicts keyed by unsigned ------------------------------------------

    @Test
    fun uByte_asDictKey_roundTripsBoundaryValues() {
        val map = uByteValues.associateWith { it.toString() }
        assertEquals(map, roundTrip(map))
    }

    @Test
    fun uShort_asDictKey_roundTripsBoundaryValues() {
        val map = uShortValues.associateWith { it.toString() }
        assertEquals(map, roundTrip(map))
    }

    @Test
    fun uInt_asDictKey_roundTripsBoundaryValues() {
        val map = uIntValues.associateWith { it.toString() }
        assertEquals(map, roundTrip(map))
    }

    @Test
    fun uLong_asDictKey_roundTripsBoundaryValues() {
        val map = uLongValues.associateWith { it.toString() }
        assertEquals(map, roundTrip(map))
    }

    // --- Structs containing unsigned --------------------------------------

    @Test
    fun unsignedStruct_roundTripsBoundaryValues() {
        val maxIndex = listOf(
            uByteValues.size,
            uShortValues.size,
            uIntValues.size,
            uLongValues.size
        ).min()
        for (i in 0 until maxIndex) {
            val struct = UnsignedStruct(
                uByteValues[i],
                uShortValues[i],
                uIntValues[i],
                uLongValues[i]
            )
            assertEquals(struct, roundTrip(struct))
        }
    }

    @Test
    fun listOfUnsignedStructs_roundTripsBoundaryValues() {
        val structs = listOf(
            UnsignedStruct(UByte.MIN_VALUE, UShort.MIN_VALUE, UInt.MIN_VALUE, ULong.MIN_VALUE),
            UnsignedStruct(127u, 30000u, 2_000_000_000u, 9_000_000_000_000_000_000uL),
            UnsignedStruct(UByte.MAX_VALUE, UShort.MAX_VALUE, UInt.MAX_VALUE, ULong.MAX_VALUE)
        )
        assertEquals(structs, roundTrip(structs))
    }

    // --- Variants wrapping unsigned ---------------------------------------

    @Test
    fun uByte_inVariant_roundTripsBoundaryValues() {
        for (value in uByteValues) {
            assertEquals(value, roundTripVariant<UByte>(value))
        }
    }

    @Test
    fun uShort_inVariant_roundTripsBoundaryValues() {
        for (value in uShortValues) {
            assertEquals(value, roundTripVariant<UShort>(value))
        }
    }

    @Test
    fun uInt_inVariant_roundTripsBoundaryValues() {
        for (value in uIntValues) {
            assertEquals(value, roundTripVariant<UInt>(value))
        }
    }

    @Test
    fun uLong_inVariant_roundTripsBoundaryValues() {
        for (value in uLongValues) {
            assertEquals(value, roundTripVariant<ULong>(value))
        }
    }

    @Test
    fun listOfUnsignedInVariant_roundTripsBoundaryValues() {
        assertEquals(uByteValues, roundTripVariant<List<UByte>>(uByteValues))
        assertEquals(uShortValues, roundTripVariant<List<UShort>>(uShortValues))
        assertEquals(uIntValues, roundTripVariant<List<UInt>>(uIntValues))
        assertEquals(uLongValues, roundTripVariant<List<ULong>>(uLongValues))
    }

    @Test
    fun dictOfUnsignedInVariant_roundTripsBoundaryValues() {
        val map = uLongValues.associateWith { it }
        assertEquals(map, roundTripVariant<Map<ULong, ULong>>(map))
    }

    @Serializable
    data class UnsignedStruct(val y: UByte, val q: UShort, val u: UInt, val t: ULong)

    private companion object {
        val uByteValues: List<UByte> = listOf(UByte.MIN_VALUE, 127u, UByte.MAX_VALUE)
        val uShortValues: List<UShort> = listOf(UShort.MIN_VALUE, 30000u, UShort.MAX_VALUE)
        val uIntValues: List<UInt> = listOf(UInt.MIN_VALUE, 2_000_000_000u, UInt.MAX_VALUE)
        val uLongValues: List<ULong> =
            listOf(ULong.MIN_VALUE, 9_000_000_000_000_000_000uL, ULong.MAX_VALUE)

        inline fun <reified T : Any> roundTrip(value: T): T {
            val msg = PlainMessage.createPlainMessage()
            msg.serialize(value)
            msg.seal()
            return msg.deserialize<T>()
        }

        inline fun <reified T : Any> roundTripVariant(value: T): T {
            val msg = PlainMessage.createPlainMessage()
            msg.serialize(Variant(value))
            msg.seal()
            return msg.deserialize<Variant>().get<T>()
        }
    }
}
