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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

class DBusMethodTests : BaseTest() {
    private val fixture: ConnectionTestFixture = TestFixtureSdBusCppLoop(this)

    @Test
    fun CallsEmptyMethodSuccesfully(): Unit = memScoped {
        fixture.m_proxy!!.noArgNoReturn()
    }

    @Test
    fun CallsMethodsWithBaseTypesSuccesfully(): Unit = memScoped {
        val resInt = fixture.m_proxy!!.getInt();
        assertEquals(INT32_VALUE, resInt)

        val multiplyRes = fixture.m_proxy!!.multiply(INT64_VALUE, DOUBLE_VALUE);
        assertEquals(INT64_VALUE * DOUBLE_VALUE, multiplyRes)
    }

    @Test
    fun CallsMethodsWithTuplesSuccesfully(): Unit = memScoped {
        val resTuple = fixture.m_proxy!!.getTuple();
        assertEquals(UINT32_VALUE, resTuple.first)
        assertEquals(STRING_VALUE, resTuple.second)
    }

//    @Test
//    fun CallsMethodsWithStructSuccesfully(): Unit = memScoped {
//        Struct < uint8_t, int16_t, double, std::string, std::vector<int16_t>> a{};
//        val vectorRes = fixture.m_proxy.getInts16FromStruct(a);
//        assertEquals(
//            std::vector<int16_t>{ 0 },
//            vectorRes
//        ) // because second item is by default initialized to 0
//
//        Struct < uint8_t, int16_t, double, std::string, std::vector<int16_t>> b{
//        UINT8_VALUE, INT16_VALUE, DOUBLE_VALUE, STRING_VALUE, { INT16_VALUE, -INT16_VALUE }
//    };
//        vectorRes = fixture.m_proxy.getInts16FromStruct(b);
//        assertEquals(std::vector<int16_t>{ INT16_VALUE, INT16_VALUE, -INT16_VALUE }, vectorRes)
//    }

    @Test
    fun CallsMethodWithVariantSuccesfully(): Unit = memScoped {
        val v = Variant(DOUBLE_VALUE);
        val variantRes = fixture.m_proxy!!.processVariant(v);
        assertEquals(DOUBLE_VALUE.toInt(), variantRes.get<Int>())
    }

//    @Test
//    fun CallsMethodWithStructVariantsAndGetMapSuccesfully(): Unit = memScoped {
//        std::vector<int32_t> x { -2, 0, 2 };
//        Struct<Variant, Variant> y { false, true };
//        std::map<int32_t, Variant> mapOfVariants = fixture . m_proxy . getMapOfVariants (x, y);
//        decltype(mapOfVariants) res {
//            { -2, Variant{ false } }
//            , { 0, Variant{ false } }
//            , { 2, Variant{ true } }
//        };
//
//        assertEquals(res[-2].get<bool>(), mapOfVariants[-2].get<bool>())
//        assertEquals(res[0].get<bool>(), mapOfVariants[0].get<bool>())
//        assertEquals(res[2].get<bool>(), mapOfVariants[2].get<bool>())
//    }

//    @Test
//    fun CallsMethodWithStructInStructSuccesfully(): Unit = memScoped {
//        val
//        val = fixture.m_proxy.getStructInStruct();
//        assertEquals(STRING_VALUE, val.template get <0>())
//        assertEquals(INT32_VALUE, std::get < 0 > (std::get < 1 > (val))[INT32_VALUE])
//    }
//
//    @Test
//    fun CallsMethodWithTwoStructsSuccesfully(): Unit = memScoped {
//        val
//        val = fixture.m_proxy.sumStructItems({ 1, 2 }, { 3, 4 });
//        assertEquals(1 + 2 + 3 + 4, val)
//    }

    @Test
    fun CallsMethodWithTwoVectorsSuccesfully(): Unit = memScoped {
        val result = fixture.m_proxy!!.sumArrayItems(
            listOf(1.toUShort(), 7.toUShort()),
            arrayOf(2u, 3u, 4u)
        );
        assertEquals(1u + 7u + 2u + 3u + 4u, result)
    }

    @Test
    fun CallsMethodWithSignatureSuccesfully(): Unit = memScoped {
        val resSignature = fixture.m_proxy!!.getSignature();
        assertEquals(SIGNATURE_VALUE, resSignature)
    }

    @Test
    fun CallsMethodWithObjectPathSuccesfully(): Unit = memScoped {
        val resObjectPath = fixture.m_proxy!!.getObjPath();
        assertEquals(OBJECT_PATH_VALUE, resObjectPath)
    }

    @Test
    fun CallsMethodWithUnixFdSuccesfully(): Unit = memScoped {
        val resUnixFd = fixture.m_proxy!!.getUnixFd();
        assertTrue(resUnixFd.fd > UNIX_FD_VALUE);
    }

//    @Test
//    fun CallsMethodWithComplexTypeSuccesfully(): Unit = memScoped {
//        val resComplex = fixture.m_proxy.getComplex();
//        assertEquals(1, resComplex.count(0))
//    }

    @Test
    fun CallsMultiplyMethodWithNoReplyFlag(): Unit = memScoped {
        fixture.m_proxy!!.multiplyWithNoReply(INT64_VALUE, DOUBLE_VALUE);

        assertTrue(waitUntil(fixture.m_adaptor!!.m_wasMultiplyCalled));
        assertEquals(INT64_VALUE * DOUBLE_VALUE, fixture.m_adaptor!!.m_multiplyResult)
    }

    @Test
    fun CallsMethodWithCustomTimeoutSuccessfully(): Unit = memScoped {
        val res = fixture.m_proxy!!.doOperationWithTimeout(
            500.milliseconds,
            20u
        ); // The operation will take 20ms, but the timeout is 500ms, so we are fine
        assertEquals(20u, res)
    }

    @Test
    fun ThrowsTimeoutErrorWhenMethodTimesOut(): Unit = memScoped {
        val start = Clock.System.now()
        try {
            fixture.m_proxy!!.doOperationWithTimeout(
                1.microseconds,
                1000u
            ); // The operation will take 1s, but the timeout is 1us, so we should time out
            fail("Expected Error exception");
        } catch (e: Error) {
            assertContains(
                listOf("org.freedesktop.DBus.Error.Timeout", "org.freedesktop.DBus.Error.NoReply"),
                e.name
            )
            assertContains(
                listOf("Connection timed out", "Operation timed out", "Method call timed out"),
                e.errorMessage,
            )
            val measuredTimeout = Clock.System.now() - start;
            assertTrue(measuredTimeout <= 50.milliseconds);
        }
    }

    @Test
    fun CallsMethodThatThrowsError(): Unit = memScoped {
        try {
            fixture.m_proxy!!.throwError();
            fail("Expected Error exception");
        } catch (e: Error) {
            assertEquals("org.freedesktop.DBus.Error.AccessDenied", e.name)
            assertEquals("A test error occurred (Operation not permitted)", e.errorMessage)
        }
    }

    @Test
    fun CallsErrorThrowingMethodWithDontExpectReplySet(): Unit = memScoped {
        fixture.m_proxy!!.throwErrorWithNoReply();

        assertTrue(waitUntil(fixture.m_adaptor!!.m_wasThrowErrorCalled));
    }

    @Test
    fun FailsCallingNonexistentMethod(): Unit = memScoped {
        try {
            fixture.m_proxy!!.callNonexistentMethod()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun FailsCallingMethodOnNonexistentInterface(): Unit = memScoped {
        try {
            fixture.m_proxy!!.callMethodOnNonexistentInterface()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun FailsCallingMethodOnNonexistentDestination(): Unit = memScoped {
        val proxy =
            TestProxy(ServiceName("sdbuscpp.destination.that.does.not.exist"), OBJECT_PATH);
        try {
            proxy.getInt()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun FailsCallingMethodOnNonexistentObject(): Unit = memScoped {
        val proxy = TestProxy(SERVICE_NAME, ObjectPath("/sdbuscpp/path/that/does/not/exist"));
        try {
            proxy.getInt()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun CanReceiveSignalWhileMakingMethodCall(): Unit = memScoped {
        fixture.m_proxy!!.emitTwoSimpleSignals();

        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal), "Got simple signal");
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSignalWithMap), "Got signal with map");
    }

    @Test
    fun CanAccessAssociatedMethodCallMessageInMethodCallHandler(): Unit = memScoped {
        fixture.m_proxy!!.doOperation(10u); // This will save pointer to method call message on server side

        assertNotNull(fixture.m_adaptor!!.m_methodCallMsg)
        assertEquals("doOperation", fixture.m_adaptor!!.m_methodName?.value)
    }

    @Test
    fun CanAccessAssociatedMethodCallMessageInAsyncMethodCallHandler(): Unit = runTest {
        fixture.m_proxy!!.doOperationAsync(10u); // This will save pointer to method call message on server side

        assertNotNull(fixture.m_adaptor!!.m_methodCallMsg);
        assertEquals("doOperationAsync", fixture.m_adaptor?.m_methodName?.value)
    }

    @Test
    fun CanSetGeneralMethodTimeoutWithLibsystemdVersionGreaterThan239(): Unit = memScoped {
        s_adaptorConnection.setMethodCallTimeout(5000000u);
        assertEquals(5000000u, s_adaptorConnection.getMethodCallTimeout())
    }

    @Test
    fun CanCallMethodSynchronouslyWithoutAnEventLoopThread(): Unit = memScoped {
        val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH, dont_run_event_loop_thread);

        val multiplyRes = proxy.multiply(INT64_VALUE, DOUBLE_VALUE);

        assertEquals(INT64_VALUE * DOUBLE_VALUE, multiplyRes)
    }

    @Test
    fun CanRegisterAdditionalVTableDynamicallyAtAnyTime(): Unit = memScoped {
        val obj = fixture.m_adaptor!!.m_object;
        val interfaceName = InterfaceName("org.sdbuscpp.integrationtests2");
        val vtableSlot = obj.addVTable(
            interfaceName,
            listOf(
                registerMethod("add").implementedAs { call { a: Long, b: Double -> a + b; } },
                registerMethod("subtract").implementedAs { call { a: Int, b: Int -> a - b; } },
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
    }

    @Test
    fun CanUnregisterAdditionallyRegisteredVTableAtAnyTime(): Unit = memScoped {
        val obj = fixture.m_adaptor!!.m_object;
        val interfaceName = InterfaceName("org.sdbuscpp.integrationtests2");

        memScoped {
            val slot = obj.addVTable(interfaceName, listOf(
                registerMethod("add").implementedAs { call { a: Long, b: Double -> a + b; } },
                registerMethod("subtract").implementedAs { call { a: Int, b: Int -> a - b; } }
            ), return_slot)
        }

        // No such remote D-Bus method under given interface exists anymore...
        val proxy = createProxy(SERVICE_NAME, OBJECT_PATH, dont_run_event_loop_thread)
        try {
            memScoped {
                proxy.callMethod("subtract").onInterface(interfaceName)
                    .withArguments { call(10, 2) }
            }
            fail("Method did not throw")
        } catch (t: Throwable) {
            // Expected
        }
    }
}
