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

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi

class DBusSignalsTest : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    @Test
    fun emitsSimpleSignalSuccesfully() {
        fixture.adaptor!!.emitSimpleSignal()

        assertTrue(waitUntil(fixture.proxy!!.gotSimpleSignal))
    }

    @Test
    fun emitsSimpleSignalToMultipleProxiesSuccesfully() {
        val proxy1 = TestProxy(globalAdaptorConnection, SERVICE_NAME, OBJECT_PATH)
        val proxy2 = TestProxy(globalAdaptorConnection, SERVICE_NAME, OBJECT_PATH)
        proxy1.registerProxy()
        proxy2.registerProxy()

        fixture.adaptor!!.emitSimpleSignal()

        assertTrue(waitUntil(fixture.proxy!!.gotSimpleSignal))
        assertTrue(waitUntil(proxy1.gotSimpleSignal))
        assertTrue(waitUntil(proxy2.gotSimpleSignal))
    }

    @Test
    fun proxyDoesNotReceiveSignalFromOtherBusName() {
        val otherBusName = ServiceName(SERVICE_NAME.value + "2")
        val connection2 = com.monkopedia.sdbus.createBusConnection(otherBusName)
        val adaptor2 = TestAdaptor(connection2, OBJECT_PATH)

        adaptor2.emitSimpleSignal()

        assertFalse(waitUntil(fixture.proxy!!.gotSimpleSignal, 1.seconds))
    }

    @Test
    fun emitsSignalWithMapSuccesfully() {
        fixture.adaptor!!.emitSignalWithMap(mapOf(0 to "zero", 1 to "one"))

        assertTrue(waitUntil(fixture.proxy!!.gotSignalWithMap))
        assertEquals("zero", fixture.proxy!!.mapFromSignal[0])
        assertEquals("one", fixture.proxy!!.mapFromSignal[1])
    }

    @Test
    fun emitsSignalWithLargeMapSuccesfully() {
        val largeMap = mutableMapOf<Int, String>()
        for (i in 0 until 20000) {
            largeMap[i] = "This is string nr. ${(i + 1)}"
        }
        fixture.adaptor!!.emitSignalWithMap(largeMap)

        assertTrue(waitUntil(fixture.proxy!!.gotSignalWithMap, timeout = 20.seconds))
        assertEquals("This is string nr. 1", fixture.proxy!!.mapFromSignal[0])
        assertEquals("This is string nr. 2", fixture.proxy!!.mapFromSignal[1])
    }

    @Test
    fun emitsSignalWithVariantSuccesfully() {
        val d = 3.14
        fixture.adaptor!!.emitSignalWithVariant(Variant(d))

        assertTrue(waitUntil(fixture.proxy!!.gotSignalWithVariant))
        assertEquals(d, fixture.proxy!!.variantFromSignal, .01)
    }

    @Test
    fun canAccessAssociatedSignalMessageInSignalHandler() {
        fixture.adaptor!!.emitSimpleSignal()

        waitUntil(fixture.proxy!!.gotSimpleSignal)

        assertNotNull(fixture.proxy!!.signalMsg)
        assertEquals("simpleSignal", fixture.proxy!!.signalName?.value)
    }

    @Test
    fun unregistersSignalHandler() {
        fixture.proxy!!.unregisterSimpleSignalHandler()

        fixture.adaptor!!.emitSimpleSignal()

        assertFalse(waitUntil(fixture.proxy!!.gotSimpleSignal, 1.seconds))
    }

    @Test
    fun unregistersSignalHandlerForSomeProxies() {
        val proxy1 = TestProxy(globalAdaptorConnection, SERVICE_NAME, OBJECT_PATH)
        val proxy2 = TestProxy(globalAdaptorConnection, SERVICE_NAME, OBJECT_PATH)
        proxy1.registerProxy()
        proxy2.registerProxy()

        fixture.proxy!!.unregisterSimpleSignalHandler()

        fixture.adaptor!!.emitSimpleSignal()

        assertTrue(waitUntil(proxy1.gotSimpleSignal))
        assertTrue(waitUntil(proxy2.gotSimpleSignal))
        assertFalse(waitUntil(fixture.proxy!!.gotSimpleSignal, 1.seconds))
    }

    @Test
    fun reRegistersSignalHandler() {
        // unregister simple-signal handler
        fixture.proxy!!.unregisterSimpleSignalHandler()

        fixture.adaptor!!.emitSimpleSignal()

        assertFalse(waitUntil(fixture.proxy!!.gotSimpleSignal, 1.seconds))

        // re-register simple-signal handler
        fixture.proxy!!.reRegisterSimpleSignalHandler()

        fixture.adaptor!!.emitSimpleSignal()

        assertTrue(waitUntil(fixture.proxy!!.gotSimpleSignal))
    }
}
