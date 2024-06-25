@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.MessageTest.ComplexTypeForMessageValue
import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PlainMessage
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.UnixFd
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.deserialize
import com.monkopedia.sdbus.header.serialize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.serialization.Serializable

typealias ComplexType = Map<ULong, ComplexTypeForMessageValue>

class MessageTest {

    fun ASSERT_NO_THROW(any: Any?) = any

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun `AMessage CanBeDefaultConstructed`(): Unit = memScoped {
        ASSERT_NO_THROW(PlainMessage.createPlainMessage())
    }

//    @Test fun `AMessage IsInvalidAfterDefaultConstructed`(): Unit = memScoped {
//        sdbus::PlainMessage msg;
//
//        assertFalse(msg.isValid());
//    }

//    @Test fun `AMessage IsValidWhenConstructedAsRealMessage`(): Unit = memScoped
//    {
//        val msg = createPlainMessage()
//        assertTrue(msg.isValid());
//    }

    @Test
    fun `AMessage CreatesShallowCopyWhenCopyConstructed`(): Unit = memScoped {
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
    fun `AMessage CreatesDeepCopyWhenEplicitlyCopied`(): Unit = memScoped {
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
    fun `AMessage IsEmptyWhenContainsNoValue`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        assertTrue(msg.isEmpty())
    }

    @Test
    fun `AMessage IsNotEmptyWhenContainsAValue`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize("I am a string")

        assertFalse(msg.isEmpty())
    }

    @Test
    fun `AMessage CanCarryASimpleInteger`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = 5

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Int>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryAStringAsAStringView`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = "Hello"

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<String>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryAUnixFd`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = UnixFd(0)
        msg.serialize(dataWritten)

        msg.seal()

        val dataRead = msg.deserialize<UnixFd>()

        assertTrue(dataRead.fd > dataWritten.fd)
    }

    @Test
    fun `AMessage CanCarryAVariant`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = Variant(3.14)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Variant>()

        assertEquals(dataWritten.get<Double>(), dataRead.get<Double>())
    }

    @Test
    fun `AMessage CanCarryACollectionOfEmbeddedVariants`(): Unit = memScoped {
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
    fun `AMessage CanCarryDBusArrayOfTrivialTypesGivenAsStdVector`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = listOf(3545342, 43643532, 324325)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<List<Int>>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfNontrivialTypesGivenAsStdVector`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = listOf(Signature("s"), Signature("u"), Signature("b"))

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<List<Signature>>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfTrivialTypesGivenAsStdArray`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = arrayOf(3545342, 43643532, 324325)

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Array<Int>>()

        assertEquals(dataWritten.toList(), dataRead.toList())
    }

    @Test
    fun `AMessage CanCarrySignature`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = Signature("s")

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Signature>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusArrayOfNontrivialTypesGivenAsStdArray`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = arrayOf(Signature("s"), Signature("u"), Signature("b"))

        msg.serialize(dataWritten)
        msg.seal()

        val dataRead = msg.deserialize<Array<Signature>>()

        assertEquals(dataWritten.toList(), dataRead.toList())
    }

    @Test
    fun `AMessage CanCarryAnEnumValue`(): Unit = memScoped {
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

//    #ifdef __cpp_lib_span
//    @Test
//    fun `AMessage CanCarryDBusArrayOfTrivialTypesGivenAsStdSpan`(): Unit = memScoped {
//        val msg = createPlainMessage()
//
//        const std ::array < int, 3> sourceArray{ 3545342, 43643532, 324325 };
//        const std ::span dataWritten { sourceArray };
//
//        msg.serialize(dataWritten)
//        msg.seal();
//
//        std::array < int, 3> destinationArray;
//        val dataRead = msg.deserialize < std::span > ()
//
//        assertEquals(
//            std::vector(dataWritten.begin(), dataWritten.end()),
//            std::vector(dataRead.begin(), dataRead.end())
//        )
//    }
//
//    @Test
//    fun `AMessage CanCarryDBusArrayOfNontrivialTypesGivenAsStdSpan`(): Unit = memScoped
//    {
//        val msg = createPlainMessage()
//
//        const std ::array sourceArray { sdbus::Signature{ "s" }, sdbus::Signature{ "u" }, sdbus::Signature{ "b" } };
//        const std ::span dataWritten { sourceArray };
//
//        msg.serialize(dataWritten)
//        msg.seal();
//
//        std::array < sdbus::Signature, 3> destinationArray;
//        val dataRead = msg.deserialize < std::span > ()
//
//        assertEquals(
//            std::vector(dataWritten.begin(), dataWritten.end()),
//            std::vector(dataRead.begin(), dataRead.end())
//        )
//    }
//    #endif

//    @Test
//    fun `AMessage ThrowsWhenDestinationStdArrayIsTooSmallDuringDeserialization`(): Unit = memScoped {
//        val msg = createPlainMessage()
//
//        const std ::vector<int> dataWritten { 3545342, 43643532, 324325, 89789, 15343 };
//
//        msg.serialize(dataWritten)
//        msg.seal();
//
//        std::array < int, 3> dataRead;
//        try {
//            msg.deserialize(typeOf<Array<Int>>)
//        }
//        ASSERT_THROW(msg > > dataRead, sdbus::Error);
//    }

//    #ifdef __cpp_lib_span
//    @Test
//    fun `AMessage ThrowsWhenDestinationStdSpanIsTooSmallDuringDeserialization`(): Unit = memScoped {
//        val msg = createPlainMessage()
//
//        const std ::array < int, 3> dataWritten{ 3545342, 43643532, 324325 };
//
//        msg.serialize(dataWritten)
//        msg.seal();
//
//        std::array < int, 2> destinationArray;
//        std::span dataRead { destinationArray };
//        try {
//            msg.deserialize
//        }
//        ASSERT_THROW(msg > > dataRead, sdbus::Error);
//    }
//    #endif

    @Test
    fun `AMessage CanCarryADictionary`(): Unit = memScoped {
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
//        val path: ObjectPath,
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
    fun `AMessage CanCarryAComplexType`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = mapOf(
            1.toULong() to
                ComplexTypeForMessageValue(
                    mapOf(
                        5.toUByte() to listOf(
                            InnerComplexType(
//                                ObjectPath("/some/object"),
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
    fun `AMessage CanPeekASimpleType`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(123)
        msg.seal()

        val (type, contents) = msg.peekType()

        assertEquals('i', type)
        assertNull(contents)
    }

    @Test
    fun `AMessage CanPeekContainerContents`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()
        msg.serialize(mapOf(1 to "one", 2 to "two"))
        msg.seal()

        val (type, contents) = msg.peekType()

        assertEquals('a', type)
        assertEquals("{is}", contents)
    }

    @Test
    fun `AMessage CanCarryDBusArrayGivenAsCustomType`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        val dataWritten = listOf(3545342.toLong(), 43643532.toLong(), 324325.toLong())
        // custom::MyType t;

        msg.serialize(dataWritten)
        // msg << t;
        msg.seal()

        val dataRead = msg.deserialize<List<Long>>()

        assertEquals(dataWritten, dataRead)
    }

    @Test
    fun `AMessage CanCarryDBusStructGivenAsCustomType`(): Unit = memScoped {
        val msg = PlainMessage.createPlainMessage()

        if (false) {
//        const my ::Struct dataWritten { 3545342, "hello"s, { 3.14, 2.4568546 }, my::Enum::Value2 };
//
//        msg.serialize(dataWritten)
//        msg.seal();
//
//        val dataRead = msg.deserialize < my::Struct > ()
//
//        assertEquals(dataWritten, dataRead)
        }
    }

//    class AMessage : public ::testing::TestWithParam<std::variant<int32_t, std::string, my::Struct>>
//    {
//    };

    fun `AMessage ThrowsWhenDestinationStdVariantHasWrongTypeDuringDeserialization`(): Unit =
        memScoped {
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

//    INSTANTIATE_TEST_SUITE_P( StringIntStruct
//    , AMessage
//    , ::testing::Values("hello"s, 1, my::Struct
//    {}));
}
