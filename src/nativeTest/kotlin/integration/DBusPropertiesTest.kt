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
    private val fixture = TestFixtureSdBusCppLoop(this)

    @Test
    fun readsReadOnlyPropertySuccesfully() {
        assertEquals(DEFAULT_STATE_VALUE, fixture.m_proxy!!.state())
    }

    @Test
    fun failsWritingToReadOnlyProperty() {
        try {
            fixture.m_proxy!!.setStateProperty("new_value")
            fail("Expected failure")
        } catch (t: Error) {
            // Expected failure
        }
    }

    @Test
    fun writesAndReadsReadWritePropertySuccesfully() {
        val newActionValue = 5678u

        fixture.m_proxy!!.action(newActionValue)

        assertEquals(newActionValue, fixture.m_proxy!!.action())
    }

    @Test
    fun canAccessAssociatedPropertySetMessageInPropertySetHandler() {
        // This will save pointer to property get message on server side
        fixture.m_proxy!!.blocking(true)

        assertNotNull(fixture.m_adaptor!!.m_propertySetMsg)
        assertFalse(fixture.m_adaptor!!.m_propertySetSender!!.isEmpty())
    }
}
