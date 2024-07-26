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
