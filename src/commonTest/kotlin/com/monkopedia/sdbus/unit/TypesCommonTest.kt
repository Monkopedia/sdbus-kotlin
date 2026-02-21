package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PlainMessage.Companion.createPlainMessage
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.containsValueOfType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ANY_UINT64: ULong = 84578348354u
private const val ANY_DOUBLE: Double = 3.14
private typealias TypesComplexType = Map<ULong, List<Pair<String, Double>>>
private typealias TypeWithVariants = List<Pair<Variant, Double>>

class TypesCommonTest {
    @Test
    fun aVariant_CanBeDefaultConstructed() {
        Variant()
    }

    @Test
    fun aVariant_ContainsNoValueAfterDefaultConstructed() {
        assertTrue(Variant().isEmpty)
    }

    @Test
    fun aVariant_CanBeConstructedFromASimpleValue() {
        Variant(5)
    }

    @Test
    fun aVariant_CanBeConstructedFromAComplexValue() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))
        Variant(value)
    }

    @Test
    fun aVariant_IsNotEmptyWhenContainsAValue() {
        assertFalse(Variant("hello").isEmpty)
    }

    @Test
    fun aSimpleVariant_ReturnsTheSimpleValueWhenAsked() {
        val value = 5
        val variant = Variant(value)
        assertEquals(value, variant.get<Int>())
    }

    @Test
    fun aComplexVariant_ReturnsTheComplexValueWhenAsked() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))
        val variant = Variant(value)
        assertEquals(value, variant.get<TypesComplexType>())
    }

    @Test
    fun aVariant_HasConceptuallyNonmutableGetMethodWhichCanBeCalledXTimes() {
        val value = "I am a string"
        val variant = Variant(value)
        assertEquals(value, variant.get<String>())
        assertEquals(value, variant.get<String>())
        assertEquals(value, variant.get<String>())
    }

    @Test
    fun aVariant_ReturnsTrueWhenAskedIfItContainsTheTypeItReallyContains() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))
        val variant = Variant(value)
        assertTrue(variant.containsValueOfType<TypesComplexType>())
    }

    @Test
    fun aSimpleVariant_ReturnsFalseWhenAskedIfItContainsTypeItDoesntReallyContain() {
        val variant = Variant(5)
        assertFalse(variant.containsValueOfType<Double>())
    }

    @Test
    fun aVariant_CanContainOtherEmbeddedVariants() {
        val value: TypeWithVariants = listOf(
            Variant("a string") to ANY_DOUBLE,
            Variant(ANY_UINT64) to ANY_DOUBLE
        )
        val variant = Variant(value)
        assertTrue(variant.containsValueOfType<TypeWithVariants>())
    }

    @Test
    fun aNonEmptyVariant_SerializesSuccessfullyToAMessage() {
        val variant = Variant("a string")
        val msg = createPlainMessage()
        variant.serializeTo(msg)
    }

    @Test
    fun anEmptyVariant_ThrowsWhenBeingSerializedToAMessage() {
        val variant = Variant()
        val msg = createPlainMessage()
        try {
            variant.serializeTo(msg)
        } catch (_: Error) {
            return
        }
        kotlin.test.fail("Expected serialization to fail for empty variant")
    }

    @Test
    fun aNonEmptyVariant_SerializesToAndDeserializesFromAMessageSuccessfully() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))
        val variant = Variant(value)
        val msg = createPlainMessage()
        variant.serializeTo(msg)
        msg.seal()
        val variant2 = Variant()
        variant2.deserializeFrom(msg)
        assertEquals(value, variant2.get<TypesComplexType>())
    }

    @Test
    fun anObjectPath_CanBeConstructedFromCString() {
        val aPath = "/some/path"
        assertEquals(aPath, ObjectPath(aPath).toString())
    }

    @Test
    fun aSignature_CanBeConstructedFromCString() {
        val aSignature = "us"
        assertEquals(aSignature, Signature(aSignature).toString())
    }

    @Test
    fun anError_CanBeConstructedFromANameAndAMessage() {
        val error = Error("org.sdbuscpp.error", "message")
        assertEquals("org.sdbuscpp.error", error.name)
        assertEquals("message", error.errorMessage)
    }

    @Test
    fun anError_CanBeConstructedFromANameOnly() {
        val error1 = Error("org.sdbuscpp.error")
        val error2 = Error("org.sdbuscpp.error", "")
        assertEquals("org.sdbuscpp.error", error1.name)
        assertEquals("org.sdbuscpp.error", error2.name)
        assertEquals("", error1.errorMessage)
        assertEquals("", error2.errorMessage)
    }
}
