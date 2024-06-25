@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.callMethod
import com.monkopedia.sdbus.header.createProxy
import com.monkopedia.sdbus.header.dont_run_event_loop_thread
import com.monkopedia.sdbus.header.registerMethod
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.IntStruct
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

class DBusMethodTests : BaseTest() {
    private val fixture: ConnectionTestFixture = TestFixtureSdBusCppLoop(this)

    @Test
    fun callsEmptyMethodSuccesfully() {
        fixture.m_proxy!!.noArgNoReturn()
    }

    @Test
    fun callsMethodsWithBaseTypesSuccesfully() {
        val resInt = fixture.m_proxy!!.getInt()
        assertEquals(INT32_VALUE, resInt)

        val multiplyRes = fixture.m_proxy!!.multiply(INT64_VALUE, DOUBLE_VALUE)
        assertEquals(INT64_VALUE * DOUBLE_VALUE, multiplyRes)
    }

    @Test
    fun callsMethodsWithTuplesSuccesfully() {
        val resTuple = fixture.m_proxy!!.getTuple()
        assertEquals(UINT32_VALUE, resTuple.first)
        assertEquals(STRING_VALUE, resTuple.second)
    }

    @Test
    fun callsMethodsWithStructSuccesfully() {
        val a = IntStruct(0u, 0, 0.0, "", emptyList())
        var vectorRes = fixture.m_proxy?.getInts16FromStruct(a)
        assertEquals(
            listOf(0.toShort()),
            vectorRes
        ) // because second item is by default initialized to 0

        val b = IntStruct(
            UINT8_VALUE,
            INT16_VALUE,
            DOUBLE_VALUE,
            STRING_VALUE,
            listOf(INT16_VALUE, (-INT16_VALUE).toShort())
        )
        vectorRes = fixture.m_proxy?.getInts16FromStruct(b)
        assertEquals(listOf(INT16_VALUE, INT16_VALUE, (-INT16_VALUE).toShort()), vectorRes)
    }

    @Test
    fun callsMethodWithVariantSuccesfully() {
        val v = Variant(DOUBLE_VALUE)
        val variantRes = fixture.m_proxy!!.processVariant(v)
        assertEquals(DOUBLE_VALUE.toInt(), variantRes.get<Int>())
    }

    @Test
    fun callsMethodWithStructVariantsAndGetMapSuccesfully() {
        val x = listOf(-2, 0, 2)
        val y = Variant(false) to Variant(true)
        val mapOfVariants = fixture.m_proxy?.getMapOfVariants(x, y)!!
        val res = mapOf(
            -2 to Variant(false),
            0 to Variant(false),
            2 to Variant(true)
        )

        assertEquals(res[-2]?.get<Boolean>(), mapOfVariants[-2]?.get<Boolean>())
        assertEquals(res[0]?.get<Boolean>(), mapOfVariants[0]?.get<Boolean>())
        assertEquals(res[2]?.get<Boolean>(), mapOfVariants[2]?.get<Boolean>())
    }

    @Test
    fun callsMethodWithStructInStructSuccesfully() {
        val v = fixture.m_proxy?.getStructInStruct()!!
        assertEquals(STRING_VALUE, v.first)
        assertEquals(INT32_VALUE, v.second.first[INT32_VALUE])
    }

    @Test
    fun callsMethodWithTwoStructsSuccesfully() {
        val v = fixture.m_proxy?.sumStructItems(1.toUByte() to 2.toUShort(), 3 to 4)
        assertEquals(1 + 2 + 3 + 4, v)
    }

    @Test
    fun callsMethodWithTwoVectorsSuccesfully() {
        val result = fixture.m_proxy!!.sumArrayItems(
            listOf(1.toUShort(), 7.toUShort()),
            arrayOf(2u, 3u, 4u)
        )
        assertEquals(1u + 7u + 2u + 3u + 4u, result)
    }

    @Test
    fun callsMethodWithSignatureSuccesfully() {
        val resSignature = fixture.m_proxy!!.getSignature()
        assertEquals(SIGNATURE_VALUE, resSignature)
    }

    @Test
    fun callsMethodWithObjectPathSuccesfully() {
        val resObjectPath = fixture.m_proxy!!.getObjPath()
        assertEquals(OBJECT_PATH_VALUE, resObjectPath)
    }

    @Test
    fun callsMethodWithUnixFdSuccesfully() {
        val resUnixFd = fixture.m_proxy!!.getUnixFd()
        assertTrue(resUnixFd.fd > UNIX_FD_VALUE)
    }

    @Test
    fun callsMethodWithComplexTypeSuccesfully() {
        val resComplex = fixture.m_proxy?.getComplex()
        assertEquals(1, resComplex?.size)
    }

    @Test
    fun callsMultiplyMethodWithNoReplyFlag() {
        fixture.m_proxy!!.multiplyWithNoReply(INT64_VALUE, DOUBLE_VALUE)

        assertTrue(waitUntil(fixture.m_adaptor!!.m_wasMultiplyCalled))
        assertEquals(INT64_VALUE * DOUBLE_VALUE, fixture.m_adaptor!!.m_multiplyResult)
    }

    @Test
    fun callsMethodWithCustomTimeoutSuccessfully() {
        val res = fixture.m_proxy!!.doOperationWithTimeout(
            500.milliseconds,
            20u
        ) // The operation will take 20ms, but the timeout is 500ms, so we are fine
        assertEquals(20u, res)
    }

    @Test
    fun ThrowsTimeoutErrorWhenMethodTimesOut() {
        val start = Clock.System.now()
        try {
            fixture.m_proxy!!.doOperationWithTimeout(
                1.microseconds,
                1000u
            ) // The operation will take 1s, but the timeout is 1us, so we should time out
            fail("Expected Error exception")
        } catch (e: Error) {
            assertContains(
                listOf("org.freedesktop.DBus.Error.Timeout", "org.freedesktop.DBus.Error.NoReply"),
                e.name
            )
            assertContains(
                listOf("Connection timed out", "Operation timed out", "Method call timed out"),
                e.errorMessage
            )
            val measuredTimeout = Clock.System.now() - start
            assertTrue(measuredTimeout <= 50.milliseconds)
        }
    }

    @Test
    fun callsMethodThatThrowsError() {
        try {
            fixture.m_proxy!!.throwError()
            fail("Expected Error exception")
        } catch (e: Error) {
            assertEquals("org.freedesktop.DBus.Error.AccessDenied", e.name)
            assertEquals("A test error occurred (Operation not permitted)", e.errorMessage)
        }
    }

    @Test
    fun callsErrorThrowingMethodWithDontExpectReplySet() {
        fixture.m_proxy!!.throwErrorWithNoReply()

        assertTrue(waitUntil(fixture.m_adaptor!!.m_wasThrowErrorCalled))
    }

    @Test
    fun failsCallingNonexistentMethod() {
        try {
            fixture.m_proxy!!.callNonexistentMethod()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun failsCallingMethodOnNonexistentInterface() {
        try {
            fixture.m_proxy!!.callMethodOnNonexistentInterface()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun failsCallingMethodOnNonexistentDestination() {
        val proxy =
            TestProxy(ServiceName("sdbuscpp.destination.that.does.not.exist"), OBJECT_PATH)
        try {
            proxy.getInt()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun failsCallingMethodOnNonexistentObject() {
        val proxy = TestProxy(SERVICE_NAME, ObjectPath("/sdbuscpp/path/that/does/not/exist"))
        try {
            proxy.getInt()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun canReceiveSignalWhileMakingMethodCall() {
        fixture.m_proxy!!.emitTwoSimpleSignals()

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal), "Got simple signal")
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSignalWithMap), "Got signal with map")
    }

    @Test
    fun canAccessAssociatedMethodCallMessageInMethodCallHandler() {
        // This will save pointer to method call message on server side
        fixture.m_proxy!!.doOperation(10u)

        assertNotNull(fixture.m_adaptor!!.m_methodCallMsg)
        assertEquals("doOperation", fixture.m_adaptor!!.m_methodName?.value)
    }

    @Test
    fun canAccessAssociatedMethodCallMessageInAsyncMethodCallHandler(): Unit = runTest {
        // This will save pointer to method call message on server side
        fixture.m_proxy!!.doOperationAsync(10u)

        assertNotNull(fixture.m_adaptor!!.m_methodCallMsg)
        assertEquals("doOperationAsync", fixture.m_adaptor?.m_methodName?.value)
    }

    @Test
    fun canSetGeneralMethodTimeoutWithLibsystemdVersionGreaterThan239() {
        s_adaptorConnection.setMethodCallTimeout(5000000u)
        assertEquals(5000000u, s_adaptorConnection.getMethodCallTimeout())
    }

    @Test
    fun canCallMethodSynchronouslyWithoutAnEventLoopThread() {
        val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH, dont_run_event_loop_thread)

        val multiplyRes = proxy.multiply(INT64_VALUE, DOUBLE_VALUE)

        assertEquals(INT64_VALUE * DOUBLE_VALUE, multiplyRes)
    }

    @Test
    fun canRegisterAdditionalVTableDynamicallyAtAnyTime() {
        val obj = fixture.m_adaptor!!.m_object
        val interfaceName = InterfaceName("org.sdbuscpp.integrationtests2")
        val vtableSlot = obj.addVTable(
            interfaceName,
            listOf(
                registerMethod("add").implementedAs {
                    call { a: Long, b: Double ->
                        a + b
                    }
                },
                registerMethod("subtract").implementedAs {
                    call { a: Int, b: Int ->
                        a - b
                    }
                }
            ),
            return_slot
        )

        // The new remote vtable is registered as long as we keep vtableSlot, so remote method calls now should pass
        val proxy = createProxy(SERVICE_NAME, OBJECT_PATH, dont_run_event_loop_thread)
        val result: Int =
            proxy.callMethod("subtract").onInterface(interfaceName)
                .withArguments { call(10, 2) }
                .readResult()

        assertEquals(8, result)
        vtableSlot.release()
    }

    @Test
    fun canUnregisterAdditionallyRegisteredVTableAtAnyTime() {
        val obj = fixture.m_adaptor!!.m_object
        val interfaceName = InterfaceName("org.sdbuscpp.integrationtests2")

        val slot = obj.addVTable(
            interfaceName,
            listOf(
                registerMethod("add").implementedAs {
                    call { a: Long, b: Double ->
                        a + b
                    }
                },
                registerMethod("subtract").implementedAs {
                    call { a: Int, b: Int ->
                        a - b
                    }
                }
            ),
            return_slot
        )
        slot.release()

        // No such remote D-Bus method under given interface exists anymore...
        val proxy = createProxy(SERVICE_NAME, OBJECT_PATH, dont_run_event_loop_thread)
        try {
            proxy.callMethod("subtract").onInterface(interfaceName)
                .withArguments { call(10, 2) }
            fail("Method did not throw")
        } catch (t: Throwable) {
            // Expected
        }
    }
}
