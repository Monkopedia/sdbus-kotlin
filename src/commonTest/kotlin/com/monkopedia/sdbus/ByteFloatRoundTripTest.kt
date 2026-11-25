package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

/**
 * Round-trip coverage for the two Kotlin primitives that have no D-Bus type of their own (#286):
 * `Byte` travels as `y` (backed by `UByte`) and `Float` as `d` (backed by `Double`).
 *
 * Scalars always worked. Arrays of them went through the native trivial-array fast path, which
 * stored the unconverted value and threw a [ClassCastException]: `List<Float>`/`FloatArray` on
 * encode and decode, `List<Byte>`/`ByteArray` on decode. `List<UByte>` and `List<Double>` use the
 * same fast path and converters, so they are kept here as controls.
 */
class ByteFloatRoundTripTest {

    @Test
    fun byte_scalar_roundTrips() {
        for (value in byteValues) {
            assertEquals(value, roundTrip(value))
        }
    }

    @Test
    fun float_scalar_roundTrips() {
        for (value in floatValues) {
            assertEquals(value, roundTrip(value))
        }
    }

    @Test
    fun byte_list_roundTrips() = assertEquals(byteValues, roundTrip(byteValues))

    @Test
    fun float_list_roundTrips() = assertEquals(floatValues, roundTrip(floatValues))

    @Test
    fun byteArray_roundTrips() {
        val value = byteValues.toByteArray()
        assertContentEquals(value, roundTrip(value))
    }

    @Test
    fun floatArray_roundTrips() {
        val value = floatValues.toFloatArray()
        assertContentEquals(value, roundTrip(value))
    }

    @Test
    fun byteStruct_roundTrips() {
        val value = ByteStruct(Byte.MIN_VALUE, byteValues)
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun floatStruct_roundTrips() {
        val value = FloatStruct(-2.5f, floatValues)
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun byte_list_isWrittenAsUnsignedBytes() {
        val msg = createPlainMessage()
        msg.serialize(byteValues)
        msg.seal()
        assertEquals(byteValues.map { it.toUByte() }, msg.deserialize<List<UByte>>())
    }

    @Test
    fun control_uByte_list_roundTrips() {
        val value = byteValues.map { it.toUByte() }
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun control_double_list_roundTrips() {
        val value = floatValues.map { it.toDouble() }
        assertEquals(value, roundTrip(value))
    }

    @Serializable
    data class ByteStruct(val byte: Byte, val bytes: List<Byte>)

    @Serializable
    data class FloatStruct(val float: Float, val floats: List<Float>)

    private companion object {
        val byteValues: List<Byte> = listOf(Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE)
        val floatValues: List<Float> = listOf(-2.5f, 0f, 0.1f, 1.5f, Float.MAX_VALUE)

        inline fun <reified T : Any> roundTrip(value: T): T {
            val msg = createPlainMessage()
            msg.serialize(value)
            msg.seal()
            return msg.deserialize<T>()
        }
    }
}
