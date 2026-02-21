package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PlainMessage
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.deserialize
import com.monkopedia.sdbus.serialize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.Serializable

private typealias CommonMessageComplexType =
    Map<ULong, MessageCommonTest.ComplexTypeForMessageValue>

class MessageCommonTest {
    @Test
    fun aMessage_CanBeDefaultConstructed() {
        PlainMessage.createPlainMessage()
    }

    @Test
    fun aMessage_IsEmptyWhenContainsNoValue() {
        val msg = PlainMessage.createPlainMessage()
        assertTrue(msg.isEmpty)
    }

    @Test
    fun aMessage_IsNotEmptyWhenContainsAValue() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")
        assertFalse(msg.isEmpty)
    }

    @Test
    fun aMessage_CanCarryASimpleInteger() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = 5
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Int>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarryAString() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = "Hello"
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<String>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarryADictionary() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = mapOf(1 to "one", 2 to "two")
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Map<Int, String>>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarryArrayOfIntegersAsList() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = listOf(3545342, 43643532, 324325)
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<List<Int>>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarryArrayOfIntegersAsArray() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = arrayOf(3545342, 43643532, 324325)
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Array<Int>>()
        assertEquals(dataWritten.toList(), dataRead.toList())
    }

    @Test
    fun aMessage_CanCarryArrayOfLongsAsList() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = listOf(3545342L, 43643532L, 324325L)
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<List<Long>>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarrySignature() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = Signature("s")
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Signature>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarryArrayOfSignaturesAsList() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = listOf(Signature("s"), Signature("u"), Signature("b"))
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<List<Signature>>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanCarryArrayOfSignaturesAsArray() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = arrayOf(Signature("s"), Signature("u"), Signature("b"))
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Array<Signature>>()
        assertEquals(dataWritten.toList(), dataRead.toList())
    }

    @Test
    fun aMessage_CanCarryUnixFd() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = UnixFd(0)
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<UnixFd>()
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertTrue(dataRead.fd != dataWritten.fd)
        } else {
            assertEquals(dataWritten.fd, dataRead.fd)
        }
    }

    @Test
    fun aMessage_CanCarryAVariant() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = Variant(3.14)
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Variant>()
        assertEquals(dataWritten.get<Double>(), dataRead.get<Double>())
    }

    @Test
    fun aMessage_CreatesDeepCopyWhenExplicitlyCopied() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")
        msg.seal()

        val msgCopy = PlainMessage.createPlainMessage()
        msg.copyTo(msgCopy, true)
        msgCopy.seal()
        msg.rewind(true)

        assertEquals("I am a string", msg.deserialize<String>())
        assertEquals("I am a string", msgCopy.deserialize<String>())
    }

    @Test
    fun aMessage_CreatesShallowCopyWhenCopyConstructed() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")
        msg.seal()

        val msgCopy = msg

        assertEquals("I am a string", msg.deserialize<String>())
        try {
            msgCopy.deserialize<String>()
            fail("Expected read failure from shallow copy")
        } catch (_: Error) {
            // Expected: both references share cursor state.
        }
    }

    @Test
    fun aMessage_CanCarryCollectionOfEmbeddedVariants() {
        val msg = PlainMessage.createPlainMessage()
        val value = listOf(Variant("hello"), Variant(3.14))
        val dataWritten = Variant(value)
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<Variant>()
        assertEquals(value[0].get<String>(), dataRead.get<List<Variant>>()[0].get<String>())
        assertEquals(value[1].get<Double>(), dataRead.get<List<Variant>>()[1].get<Double>())
    }

    @Test
    fun aMessage_CanCarryComplexType() {
        val msg = PlainMessage.createPlainMessage()
        val dataWritten = mapOf(
            1.toULong() to
                ComplexTypeForMessageValue(
                    mapOf(
                        5.toUByte() to listOf(
                            InnerComplexType(
                                ObjectPath("/some/object"),
                                true,
                                45,
                                mapOf(6 to "hello", 7 to "world")
                            )
                        )
                    ),
                    Signature("av"),
                    3.14
                )
        )
        msg.serialize(dataWritten)
        msg.seal()
        val dataRead = msg.deserialize<CommonMessageComplexType>()
        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun aMessage_CanPeekSimpleType() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(123)
        msg.seal()

        val (type, contents) = msg.peekType()

        assertEquals('i', type)
        assertNull(contents)
    }

    @Test
    fun aMessage_CanPeekContainerContents() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(mapOf(1 to "one", 2 to "two"))
        msg.seal()

        val (type, contents) = msg.peekType()

        assertEquals('a', type)
        assertEquals("{is}", contents)
    }

    @Test
    fun aMessage_ThrowsWhenVariantIsReadAsWrongType() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(Variant(5))
        msg.seal()

        val variant = msg.deserialize<Variant>()
        try {
            variant.get<Boolean>()
            fail("Expected variant type mismatch error")
        } catch (_: Error) {
            // Expected mismatch.
        }
    }

    @Serializable
    data class InnerComplexType(
        val path: ObjectPath,
        val b: Boolean,
        val s: Short,
        val map: Map<Int, String>
    )

    @Serializable
    data class ComplexTypeForMessageValue(
        val map: Map<UByte, List<InnerComplexType>>,
        val signature: Signature,
        val d: Double
    )
}
