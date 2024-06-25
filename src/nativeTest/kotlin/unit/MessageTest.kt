@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PlainMessage
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.UnixFd
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.deserialize
import com.monkopedia.sdbus.header.serialize
import com.monkopedia.sdbus.unit.MessageTest.ComplexTypeForMessageValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable

typealias ComplexType = Map<ULong, ComplexTypeForMessageValue>

class MessageTest {

    fun ASSERT_NO_THROW(any: Any?) = any

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun `AMessage CanBeDefaultConstructed`() {
        ASSERT_NO_THROW(PlainMessage.createPlainMessage())
    }

    @Test
    fun `AMessage CreatesShallowCopyWhenCopyConstructed`() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")
        msg.seal()

        val msgCopy = msg

        val str = msg.deserialize<String>()

        assertEquals("I am a string", str)
        try {
            msgCopy.deserialize<String>()
            fail("Should throw")
        } catch (t: Error) {
            // Expected throw
        }
    }

    @Test
    fun `AMessage CreatesDeepCopyWhenEplicitlyCopied`() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")
        msg.seal()

        val msgCopy = PlainMessage.createPlainMessage()
        msg.copyTo(msgCopy, true)
        msgCopy.seal(); // Seal to be able to read from it subsequently
        msg.rewind(true); // Rewind to the beginning after copying

        assertEquals("I am a string", msg.deserialize<String>())
        assertEquals("I am a string", msgCopy.deserialize<String>())
    }

    @Test
    fun `AMessage IsEmptyWhenContainsNoValue`() {
        val msg = PlainMessage.createPlainMessage()

        assertTrue(msg.isEmpty())
    }

    @Test
    fun `AMessage IsNotEmptyWhenContainsAValue`() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")

        assertFalse(msg.isEmpty())
    }

    @Test
    fun `AMessage CanCarryASimpleInteger`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = 5

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Int>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryAStringAsAStringView`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = "Hello"

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<String>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryAUnixFd`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = UnixFd(0)
        msg.serialize(dataWritten)

        msg.seal()

        val dataRead = msg.deserialize<UnixFd>()

        assertTrue(dataRead.fd > dataWritten.fd)
    }

    @Test
    fun `AMessage CanCarryAVariant`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = Variant(3.14)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Variant>()

        assertEquals(dataWritten.get<Double>(), dataRead.get<Double>())
    }

    @Test
    fun `AMessage CanCarryACollectionOfEmbeddedVariants`() {
        val msg = PlainMessage.createPlainMessage()

        val value = listOf(Variant("hello"), Variant(3.14))
        val dataWritten = Variant(value)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Variant>()

        println("Starting asserts")
        assertEquals(
            value[0].get<String>().also { println("Got 1") },
            dataRead.get<List<Variant>>()[0].also { println("List 1") }.get<String>()
                .also { println("Got 2") }
        )
        assertEquals(
            value[1].get<Double>().also { println("Got 3") },
            dataRead.get<List<Variant>>()[1].also { println("List 2") }.get<Double>()
                .also { println("Got 4") }
        )
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfTrivialTypesGivenAsStdVector`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = listOf(3545342, 43643532, 324325)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<List<Int>>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfNontrivialTypesGivenAsStdVector`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = listOf(Signature("s"), Signature("u"), Signature("b"))

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<List<Signature>>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfTrivialTypesGivenAsStdArray`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = arrayOf(3545342, 43643532, 324325)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Array<Int>>()

        assertEquals(dataWritten.toList(), dataRead.toList())
    }

    @Test
    fun `AMessage CanCarrySignature`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = Signature("s")

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Signature>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfNontrivialTypesGivenAsStdArray`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = arrayOf(Signature("s"), Signature("u"), Signature("b"))

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Array<Signature>>()

        assertEquals(dataWritten.toList(), dataRead.toList())
    }

    @Test
    fun `AMessage CanCarryAnEnumValue`() {
        if (false) {
//            val msg = createPlainMessage()
//
//            enum class EnumA : int16_t { X = 5 }
//            aWritten { EnumA::X };
//            enum EnumB { Y = 11 }
//            bWritten { EnumB::Y };
//
//            msg < < aWritten < < bWritten;
//            msg.seal();
//
//            EnumA aRead {};
//            EnumB bRead {};
//            msg > > aRead > > bRead;
//
//            assertEquals(aWritten, aRead)
//            assertEquals(bWritten, bRead)
        }
    }

    @Test
    fun `AMessage CanCarryADictionary`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = mapOf(1 to "one", 2 to "two")

        msg.serialize(dataWritten)
        msg.seal()

        println("Type: ${msg.peekType()}")
        val dataRead = msg.deserialize<Map<Int, String>>()

        assertEquals(dataWritten, dataRead)
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

    @Test
    fun `AMessage CanCarryAComplexType`() {
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

        val dataRead = msg.deserialize<ComplexType>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanPeekASimpleType`() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(123)
        msg.seal()

        val (type, contents) = msg.peekType()

        assertEquals('i', type)
        assertNull(contents)
    }

    @Test
    fun `AMessage CanPeekContainerContents`() {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(mapOf(1 to "one", 2 to "two"))
        msg.seal()

        val (type, contents) = msg.peekType()

        assertEquals('a', type)
        assertEquals("{is}", contents)
    }

    @Test
    fun `AMessage CanCarryDBusArrayGivenAsCustomType`() {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = listOf(3545342.toLong(), 43643532.toLong(), 324325.toLong())

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<List<Long>>()

        assertEquals(dataWritten, dataRead)
    }

    fun `AMessage ThrowsWhenDestinationStdVariantHasWrongTypeDuringDeserialization`() {
            val msg = PlainMessage.createPlainMessage()

            val dataWritten = Variant(5)

            msg.serialize(dataWritten)
            msg.seal()

            try {
                val variant = msg.deserialize<Variant>()
                variant.get<Boolean>()
                fail("Error")
            } catch (t: Error) {
                // Expected exception
            }
        }
}
