/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
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

internal class ConnectionTest {

    val sdBusIntfMock = SdBusMock().withDefaults()
    val fakeBusPtr = 1.toLong().toCPointer<sd_bus>()

    private val openHandler = { type: KType, args: Array<out Any?> ->
        @Suppress("UNCHECKED_CAST")
        (args[1] as CPointer<CPointerVar<sd_bus>>)[0] = fakeBusPtr
        1
    }

    @Test
    fun `ADefaultBusConnection OpensAndFlushesBusWhenCreated`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
        }
        val calls = sdBusIntfMock.record { defaultConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ASystemBusConnection OpensAndFlushesBusWhenCreated`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
        }
        val calls = sdBusIntfMock.record { systemConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ASessionBusConnection OpensAndFlushesBusWhenCreated`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
        }
        val calls = sdBusIntfMock.record { sessionConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ADefaultBusConnection ClosesAndUnrefsBusWhenDestructed`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
        }
        val calls = sdBusIntfMock.record {
            defaultConnection(it).release()
        }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ASystemBusConnection ClosesAndUnrefsBusWhenDestructed`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
        }
        val calls = sdBusIntfMock.record {
            systemConnection(it).release()
        }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ASessionBusConnection ClosesAndUnrefsBusWhenDestructed`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
        }
        val calls = sdBusIntfMock.record {
            sessionConnection(it).release()
        }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ADefaultBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open) returns -1
        }
        try {
            val calls = sdBusIntfMock.record { defaultConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASystemBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_system) returns -1
        }
        try {
            val calls = sdBusIntfMock.record { systemConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASessionBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_user) returns -1
        }
        try {
            val calls = sdBusIntfMock.record { sessionConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ADefaultBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
            method(SdBusMock::sd_bus_flush) returns -1
        }
        try {
            val calls = sdBusIntfMock.record { defaultConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASystemBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
            method(SdBusMock::sd_bus_flush) returns -1
        }
        try {
            val calls = sdBusIntfMock.record { systemConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }

    @Test
    fun `ASessionBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`() {
        sdBusIntfMock.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
            method(SdBusMock::sd_bus_flush) returns -1
        }
        try {
            val calls = sdBusIntfMock.record { sessionConnection(it) }
            fail("Expected failure, not $calls")
        } catch (t: Throwable) {
            // Expected failure
        }
    }
}
