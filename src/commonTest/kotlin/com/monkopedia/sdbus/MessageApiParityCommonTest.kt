package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageApiParityCommonTest {
    @Test
    fun plainMessage_supportsPrimitivePayloadRoundTrip() {
        val message = createPlainMessage()
        message.append(42)
        message.append("hello")
        message.append(true)

        message.seal()
        message.rewind(false)
        assertEquals(42, message.readInt())
        assertEquals("hello", message.readString())
        assertTrue(message.readBoolean())
    }

    @Test
    fun containerAndStructEntryOperations_supportSequentialPayload() {
        val message = createPlainMessage()

        message.openContainer("{is}")
        message.openDictEntry("is")
        message.append(1)
        message.append("one")
        message.closeDictEntry()
        message.closeContainer()
        message.seal()

        message.enterContainer("{is}")
        message.enterDictEntry("is")
        assertEquals(1, message.readInt())
        assertEquals("one", message.readString())
        message.exitDictEntry()
        message.exitContainer()
        assertTrue(message.isAtEnd(false))
    }

    // The three tests below pin the meaning of the `complete` flag, which no test distinguished on
    // either backend before #246. A variant is the container they use because it is the only
    // container both backends track as open while it is entered.
    @Test
    fun copyTo_copiesOneValueWhenNotComplete_andTheRemainderWhenComplete() {
        val source = createPlainMessage()
        source.append(1)
        source.append(2)
        source.append(3)
        source.seal()

        val single = createPlainMessage()
        source.copyTo(single, false)
        single.seal()
        assertEquals(1, single.readInt())
        assertTrue(
            single.isAtEnd(true),
            "copyTo(complete = false) must copy exactly one value, not the whole body"
        )

        val remainder = createPlainMessage()
        source.copyTo(remainder, true)
        remainder.seal()
        assertEquals(
            2,
            remainder.readInt(),
            "copyTo must copy from the read cursor, so the value already copied is not repeated"
        )
        assertEquals(3, remainder.readInt())
        assertTrue(remainder.isAtEnd(true))
    }

    @Test
    fun isAtEnd_withCompleteTrue_isFalseWhileAContainerIsStillOpen() {
        val message = createPlainMessage()
        message.openVariant("i")
        message.append(42)
        message.closeVariant()
        message.seal()

        message.enterVariant("i")
        assertEquals(42, message.deserialize<Int>())

        assertTrue(
            message.isAtEnd(false),
            "the open variant has been read out, so isAtEnd(complete = false) is true"
        )
        assertFalse(
            message.isAtEnd(true),
            "isAtEnd(complete = true) must be false while a container is still open"
        )

        message.exitVariant()
        assertTrue(message.isAtEnd(true))
    }

    @Test
    fun rewind_withCompleteFalse_rewindsTheOpenContainerNotTheMessage() {
        val message = createPlainMessage()
        message.append(7)
        message.openVariant("i")
        message.append(42)
        message.closeVariant()
        message.seal()

        assertEquals(7, message.readInt())
        message.enterVariant("i")
        assertEquals(42, message.deserialize<Int>())

        message.rewind(false)
        assertEquals(
            42,
            message.deserialize<Int>(),
            "rewind(complete = false) must rewind the open container, not the whole message"
        )

        message.exitVariant()
        message.rewind(true)
        assertEquals(7, message.readInt())
    }

    @Test
    fun deserializeArrayFast_readsListPayload() {
        val message = createPlainMessage()
        message.serialize(listOf(3, 4, 5))
        message.seal()

        val values = mutableListOf<Int>()
        message.deserializeArrayFast(signatureOf<Int>(), values)

        assertEquals(listOf(3, 4, 5), values)
    }
}
