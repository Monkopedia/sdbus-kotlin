package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.Message
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireMessageCodecTest {

    private fun roundTrip(message: WireMessage): WireMessage {
        val bytes = WireMessageCodec.encode(message)
        // Full frames must be 8-byte aligned at the header/body boundary; total length is the
        // padded header plus the body, so the encoded buffer is never empty.
        assertTrue(bytes.isNotEmpty())
        // Decode both directly and via the streaming reader to cover both code paths.
        val decoded = WireMessageCodec.decode(bytes)
        val streamed = WireMessageCodec.read(ByteArrayInputStream(bytes))
        assertEquals(decoded, streamed)
        return decoded
    }

    @Test
    fun methodCall_withStringBody_roundTrips() {
        val message = WireMessage(
            type = WireMessageType.METHOD_CALL,
            serial = 1,
            path = "/org/freedesktop/DBus",
            interfaceName = "org.freedesktop.DBus",
            member = "RequestName",
            destination = "org.freedesktop.DBus",
            signature = "su",
            body = listOf("com.example.Test", 0u.toUInt())
        )

        val decoded = roundTrip(message)

        assertEquals(WireMessageType.METHOD_CALL, decoded.type)
        assertEquals(1, decoded.serial)
        assertEquals("/org/freedesktop/DBus", decoded.path)
        assertEquals("org.freedesktop.DBus", decoded.interfaceName)
        assertEquals("RequestName", decoded.member)
        assertEquals("org.freedesktop.DBus", decoded.destination)
        assertEquals("su", decoded.signature)
        assertEquals(listOf<Any?>("com.example.Test", 0u.toUInt()), decoded.body)
    }

    @Test
    fun methodReturn_withReplySerialAndSender_roundTrips() {
        val message = WireMessage(
            type = WireMessageType.METHOD_RETURN,
            serial = 7,
            replySerial = 3,
            destination = ":1.5",
            sender = ":1.0",
            signature = "s",
            body = listOf(":1.5")
        )

        val decoded = roundTrip(message)

        assertEquals(WireMessageType.METHOD_RETURN, decoded.type)
        assertEquals(7, decoded.serial)
        assertEquals(3, decoded.replySerial)
        assertEquals(":1.5", decoded.destination)
        assertEquals(":1.0", decoded.sender)
        assertTrue(decoded.isReply)
        assertEquals(listOf<Any?>(":1.5"), decoded.body)
    }

    @Test
    fun error_messageRoundTrips() {
        val message = WireMessage(
            type = WireMessageType.ERROR,
            serial = 9,
            replySerial = 4,
            errorName = "org.freedesktop.DBus.Error.UnknownMethod",
            destination = ":1.5",
            signature = "s",
            body = listOf("No such method")
        )

        val decoded = roundTrip(message)

        assertEquals(WireMessageType.ERROR, decoded.type)
        assertEquals("org.freedesktop.DBus.Error.UnknownMethod", decoded.errorName)
        assertEquals(4, decoded.replySerial)
        assertEquals(listOf<Any?>("No such method"), decoded.body)
    }

    @Test
    fun signal_withComplexBody_roundTrips() {
        // Exercises a nested body — array of strings, a dict, and a variant — through the same
        // marshaller used on the wire, plus the UNIX_FDS header field.
        val message = WireMessage(
            type = WireMessageType.SIGNAL,
            serial = 12,
            path = "/com/example/Object",
            interfaceName = "com.example.Interface",
            member = "Changed",
            sender = ":1.3",
            unixFds = 0,
            signature = "asa{sv}",
            body = listOf(
                listOf("a", "b", "c"),
                linkedMapOf<String, Any?>(
                    "key" to Message.JvmVariantPayload("i", 42)
                )
            )
        )

        val decoded = roundTrip(message)

        assertEquals(WireMessageType.SIGNAL, decoded.type)
        assertEquals("/com/example/Object", decoded.path)
        assertEquals("com.example.Interface", decoded.interfaceName)
        assertEquals("Changed", decoded.member)
        assertEquals(0, decoded.unixFds)
        assertEquals(listOf("a", "b", "c"), decoded.body[0])
        @Suppress("UNCHECKED_CAST")
        val dict = decoded.body[1] as Map<String, Any?>
        assertEquals(Message.JvmVariantPayload("i", 42), dict["key"])
    }

    @Test
    fun emptyBody_roundTrips() {
        val message = WireMessage(
            type = WireMessageType.METHOD_CALL,
            serial = 1,
            path = "/org/freedesktop/DBus",
            interfaceName = "org.freedesktop.DBus",
            member = "Hello",
            destination = "org.freedesktop.DBus"
        )

        val decoded = roundTrip(message)

        assertEquals("Hello", decoded.member)
        assertEquals(null, decoded.signature)
        assertTrue(decoded.body.isEmpty())
    }
}
