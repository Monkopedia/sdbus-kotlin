package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageApiParityCommonTest {
    @Test
    fun plainMessage_supportsPrimitivePayloadRoundTrip() {
        val message = PlainMessage.createPlainMessage()
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
        val message = PlainMessage.createPlainMessage()

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

    @Test
    fun deserializeArrayFast_readsListPayload() {
        val message = PlainMessage.createPlainMessage()
        message.serialize(listOf(3, 4, 5))
        message.seal()

        val values = mutableListOf<Int>()
        message.deserializeArrayFast(signatureOf<Int>(), values)

        assertEquals(listOf(3, 4, 5), values)
    }
}
