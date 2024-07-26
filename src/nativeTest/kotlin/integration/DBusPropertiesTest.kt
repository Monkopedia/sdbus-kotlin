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

import com.monkopedia.sdbus.Error
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi

class DBusPropertiesTest : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    @Test
    fun readsReadOnlyPropertySuccesfully() {
        assertEquals(DEFAULT_STATE_VALUE, fixture.proxy!!.state())
    }

    @Test
    fun failsWritingToReadOnlyProperty() {
        try {
            fixture.proxy!!.setStateProperty("new_value")
            fail("Expected failure")
        } catch (t: Error) {
            // Expected failure
        }
    }

    @Test
    fun writesAndReadsReadWritePropertySuccesfully() {
        val newActionValue = 5678u

        fixture.proxy!!.action(newActionValue)

        assertEquals(newActionValue, fixture.proxy!!.action())
    }

    @Test
    fun canAccessAssociatedPropertySetMessageInPropertySetHandler() {
        // This will save pointer to property get message on server side
        fixture.proxy!!.blocking(true)

        assertNotNull(fixture.adaptor!!.propertySetMessage)
        assertFalse(fixture.adaptor!!.propertySetSender!!.isEmpty())
    }
}
