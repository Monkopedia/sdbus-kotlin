@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.createBusConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

class DBusSignalsTest : BaseTest() {
    private val fixture = TestFixtureSdBusCppLoop(this)

    @Test
    fun EmitsSimpleSignalSuccesfully() = memScoped {
        fixture.m_adaptor!!.emitSimpleSignal();

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
    }

    @Test
    fun EmitsSimpleSignalToMultipleProxiesSuccesfully() = memScoped {
        val proxy1 = TestProxy(this, s_adaptorConnection, SERVICE_NAME, OBJECT_PATH);
        val proxy2 = TestProxy(this, s_adaptorConnection, SERVICE_NAME, OBJECT_PATH);
        proxy1.registerProxy()
        proxy2.registerProxy()

        fixture.m_adaptor!!.emitSimpleSignal();

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
        assertTrue(waitUntil(proxy1.m_gotSimpleSignal));
        assertTrue(waitUntil(proxy2.m_gotSimpleSignal));
    }

    @Test
    fun ProxyDoesNotReceiveSignalFromOtherBusName() = memScoped {
        val otherBusName = ServiceName(SERVICE_NAME.value + "2");
        val connection2 = createBusConnection(otherBusName).own(this)
        val adaptor2 = TestAdaptor(this, connection2, OBJECT_PATH);

        adaptor2.emitSimpleSignal();

        assertFalse(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal, 1.seconds));
    }

    @Test
    fun EmitsSignalWithMapSuccesfully() = memScoped {
        fixture.m_adaptor!!.emitSignalWithMap(mapOf(0 to "zero", 1 to "one"));

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSignalWithMap));
        assertEquals("zero", fixture.m_proxy!!.m_mapFromSignal[0])
        assertEquals("one", fixture.m_proxy!!.m_mapFromSignal[1])
    }

    @Test
    fun EmitsSignalWithLargeMapSuccesfully() = memScoped {
        val largeMap = mutableMapOf<Int, String>()
        for (i in 0 until 20000) {
            largeMap[i] = "This is string nr. ${(i + 1)}"
        }
        fixture.m_adaptor!!.emitSignalWithMap(largeMap);

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSignalWithMap, timeout = 20.seconds));
        assertEquals("This is string nr. 1", fixture.m_proxy!!.m_mapFromSignal[0])
        assertEquals("This is string nr. 2", fixture.m_proxy!!.m_mapFromSignal[1])
    }

    @Test
    fun EmitsSignalWithVariantSuccesfully() = memScoped {
        val d = 3.14;
        fixture.m_adaptor!!.emitSignalWithVariant(Variant(d))

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSignalWithVariant));
        assertEquals(d, fixture.m_proxy!!.m_variantFromSignal, .01);
    }

//    @Test
//    fun EmitsSignalWithoutRegistrationSuccesfully() = memScoped {
//        fixture.m_adaptor!!.emitSignalWithoutRegistration({ "platform", sdbus::Signature{ "av" } });
//
//        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSignalWithSignature));
//        ASSERT_THAT(
//            fixture.m_proxy!!.m_signatureFromSignal["platform"],
//            Eq(sdbus::Signature{ "av" })
//        );
//    }

    @Test
    fun CanAccessAssociatedSignalMessageInSignalHandler() = memScoped {
        fixture.m_adaptor!!.emitSimpleSignal();

        waitUntil(fixture.m_proxy!!.m_gotSimpleSignal);

        assertNotNull(fixture.m_proxy!!.m_signalMsg);
        assertEquals("simpleSignal", fixture.m_proxy!!.m_signalName?.value);
    }

    @Test
    fun UnregistersSignalHandler() = memScoped {
        fixture.m_proxy!!.unregisterSimpleSignalHandler();

        fixture.m_adaptor!!.emitSimpleSignal();

        assertFalse(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal, 1.seconds));
    }

    @Test
    fun UnregistersSignalHandlerForSomeProxies() = memScoped {
        val proxy1 = TestProxy(this, s_adaptorConnection, SERVICE_NAME, OBJECT_PATH);
        val proxy2 = TestProxy(this, s_adaptorConnection, SERVICE_NAME, OBJECT_PATH);
        proxy1.registerProxy()
        proxy2.registerProxy()

        fixture.m_proxy!!.unregisterSimpleSignalHandler();

        fixture.m_adaptor!!.emitSimpleSignal();

        assertTrue(waitUntil(proxy1.m_gotSimpleSignal));
        assertTrue(waitUntil(proxy2.m_gotSimpleSignal));
        assertFalse(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal, 1.seconds));
    }

    @Test
    fun ReRegistersSignalHandler() = memScoped {
        // unregister simple-signal handler
        fixture.m_proxy!!.unregisterSimpleSignalHandler();

        fixture.m_adaptor!!.emitSimpleSignal();

        assertFalse(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal, 1.seconds));

        // re-register simple-signal handler
        fixture.m_proxy!!.reRegisterSimpleSignalHandler();

        fixture.m_adaptor!!.emitSimpleSignal();

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
    }

}