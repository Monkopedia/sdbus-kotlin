@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.unit

import cnames.structs.sd_bus
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.defaultConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.sessionConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.systemConnection
import com.monkopedia.sdbus.mocks.DefaultResponses.withDefaults
import com.monkopedia.sdbus.mocks.MappingHandler.Companion.configure
import com.monkopedia.sdbus.mocks.RecordingHandler.Companion.record
import com.monkopedia.sdbus.mocks.SdBusMock
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer

class ConnectionTest {

    val sdBusIntfMock_ = SdBusMock().withDefaults()
    val fakeBusPtr_ = 1.toLong().toCPointer<sd_bus>()

    private val openHandler = { type: KType, args: Array<out Any?> ->
        @Suppress("UNCHECKED_CAST")
        (args[1] as CPointer<CPointerVar<sd_bus>>)[0] = fakeBusPtr_
        1
    }

    @Test
    fun `ADefaultBusConnection OpensAndFlushesBusWhenCreated`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
        }
        val calls = sdBusIntfMock_.record { defaultConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ASystemBusConnection OpensAndFlushesBusWhenCreated`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
        }
        val calls = sdBusIntfMock_.record { systemConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ASessionBusConnection OpensAndFlushesBusWhenCreated`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
        }
        val calls = sdBusIntfMock_.record { sessionConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ADefaultBusConnection ClosesAndUnrefsBusWhenDestructed`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
        }
        val calls = sdBusIntfMock_.record {
            defaultConnection(it).release()
        }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ASystemBusConnection ClosesAndUnrefsBusWhenDestructed`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
        }
        val calls = sdBusIntfMock_.record {
            systemConnection(it).release()
        }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ASessionBusConnection ClosesAndUnrefsBusWhenDestructed`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
        }
        val calls = sdBusIntfMock_.record {
            sessionConnection(it).release()
        }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ADefaultBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open) returns -1
        }
        try {
            val calls = sdBusIntfMock_.record { defaultConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASystemBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_system) returns -1
        }
        try {
            val calls = sdBusIntfMock_.record { systemConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASessionBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_user) returns -1
        }
        try {
            val calls = sdBusIntfMock_.record { sessionConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ADefaultBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
            method(SdBusMock::sd_bus_flush) returns -1
        }
        try {
            val calls = sdBusIntfMock_.record { defaultConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASystemBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
            method(SdBusMock::sd_bus_flush) returns -1
        }
        try {
            val calls = sdBusIntfMock_.record { systemConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASessionBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`() {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
            method(SdBusMock::sd_bus_flush) returns -1
        }
        try {
            val calls = sdBusIntfMock_.record { sessionConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }
}
