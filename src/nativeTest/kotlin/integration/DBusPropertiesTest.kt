@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Error
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

class DBusPropertiesTest : BaseTest() {
    private val fixture = TestFixtureSdBusCppLoop(this)

    @Test
    fun ReadsReadOnlyPropertySuccesfully() = memScoped {
        assertEquals(DEFAULT_STATE_VALUE, fixture.m_proxy!!.state());
    }

    @Test
    fun FailsWritingToReadOnlyProperty() = memScoped {
        try {
            fixture.m_proxy!!.setStateProperty("new_value")
            fail("Expected failure")
        } catch (t: Error) {
            // Expected failure
        }
    }

    @Test
    fun WritesAndReadsReadWritePropertySuccesfully() = memScoped {
        val newActionValue = 5678u;

        fixture.m_proxy!!.action(newActionValue);

        assertEquals(newActionValue, fixture.m_proxy!!.action());
    }

    @Test
    fun CanAccessAssociatedPropertySetMessageInPropertySetHandler() = memScoped {
        fixture.m_proxy!!.blocking(true); // This will save pointer to property get message on server side

        assertNotNull(fixture.m_adaptor!!.m_propertySetMsg);
        assertFalse(fixture.m_adaptor!!.m_propertySetSender!!.isEmpty());
    }
}