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
@file:OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.IntStruct
import com.monkopedia.sdbus.method
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

class DBusMethodTests : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    @Test
    fun callsEmptyMethodSuccesfully() {
        fixture.proxy!!.noArgNoReturn()
    }

    @Test
    fun callsMethodsWithBaseTypesSuccesfully() {
        val resInt = fixture.proxy!!.getInt()
        assertEquals(INT32_VALUE, resInt)

        val multiplyRes = fixture.proxy!!.multiply(INT64_VALUE, DOUBLE_VALUE)
        assertEquals(INT64_VALUE * DOUBLE_VALUE, multiplyRes)
    }

    @Test
    fun callsMethodsWithTuplesSuccesfully() {
        val resTuple = fixture.proxy!!.getTuple()
        assertEquals(UINT32_VALUE, resTuple.first)
        assertEquals(STRING_VALUE, resTuple.second)
    }

    @Test
    fun callsMethodsWithStructSuccesfully() {
        val a = IntStruct(0u, 0, 0.0, "", emptyList())
        var vectorRes = fixture.proxy?.getInts16FromStruct(a)
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
        vectorRes = fixture.proxy?.getInts16FromStruct(b)
        assertEquals(listOf(INT16_VALUE, INT16_VALUE, (-INT16_VALUE).toShort()), vectorRes)
    }

    @Test
    fun callsMethodWithVariantSuccesfully() {
        val v = Variant(DOUBLE_VALUE)
        val variantRes = fixture.proxy!!.processVariant(v)
        assertEquals(DOUBLE_VALUE.toInt(), variantRes.get<Int>())
    }

    @Test
    fun callsMethodWithStructVariantsAndGetMapSuccesfully() {
        val x = listOf(-2, 0, 2)
        val y = Variant(false) to Variant(true)
        val mapOfVariants = fixture.proxy?.getMapOfVariants(x, y)!!
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
        val v = fixture.proxy?.getStructInStruct()!!
        assertEquals(STRING_VALUE, v.first)
        assertEquals(INT32_VALUE, v.second.first[INT32_VALUE])
    }

    @Test
    fun callsMethodWithTwoStructsSuccesfully() {
        val v = fixture.proxy?.sumStructItems(1.toUByte() to 2.toUShort(), 3 to 4)
        assertEquals(1 + 2 + 3 + 4, v)
    }

    @Test
    fun callsMethodWithTwoVectorsSuccesfully() {
        val result = fixture.proxy!!.sumArrayItems(
            listOf(1.toUShort(), 7.toUShort()),
            arrayOf(2u, 3u, 4u)
        )
        assertEquals(1u + 7u + 2u + 3u + 4u, result)
    }

    @Test
    fun callsMethodWithSignatureSuccesfully() {
        val resSignature = fixture.proxy!!.getSignature()
        assertEquals(SIGNATURE_VALUE, resSignature)
    }

    @Test
    fun callsMethodWithObjectPathSuccesfully() {
        val resObjectPath = fixture.proxy!!.getObjPath()
        assertEquals(OBJECT_PATH_VALUE, resObjectPath)
    }

    @Test
    fun callsMethodWithUnixFdSuccesfully() {
        val resUnixFd = fixture.proxy!!.getUnixFd()
        assertTrue(resUnixFd.fd > UNIX_FD_VALUE)
    }

    @Test
    fun callsMethodWithComplexTypeSuccesfully() {
        val resComplex = fixture.proxy?.getComplex()
        assertEquals(1, resComplex?.size)
    }

    @Test
    fun callsMultiplyMethodWithNoReplyFlag() {
        fixture.proxy!!.multiplyWithNoReply(INT64_VALUE, DOUBLE_VALUE)

        assertTrue(waitUntil(fixture.adaptor!!.wasMultiplyCalled))
        assertEquals(INT64_VALUE * DOUBLE_VALUE, fixture.adaptor!!.multiplyResult)
    }

    @Test
    fun callsMethodWithCustomTimeoutSuccessfully() {
        val res = fixture.proxy!!.doOperationWithTimeout(
            500.milliseconds,
            20u
        ) // The operation will take 20ms, but the timeout is 500ms, so we are fine
        assertEquals(20u, res)
    }

    @Test
    fun throwsTimeoutErrorWhenMethodTimesOut() {
        val start = Clock.System.now()
        try {
            fixture.proxy!!.doOperationWithTimeout(
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
            fixture.proxy!!.throwError()
            fail("Expected Error exception")
        } catch (e: Error) {
            assertEquals("org.freedesktop.DBus.Error.AccessDenied", e.name)
            assertEquals("A test error occurred (Operation not permitted)", e.errorMessage)
        }
    }

    @Test
    fun callsErrorThrowingMethodWithDontExpectReplySet() {
        fixture.proxy!!.throwErrorWithNoReply()

        assertTrue(waitUntil(fixture.adaptor!!.wasThrowErrorCalled))
    }

    @Test
    fun failsCallingNonexistentMethod() {
        try {
            fixture.proxy!!.callNonexistentMethod()
            fail("Expected error")
        } catch (t: Error) {
            // Expected error
        }
    }

    @Test
    fun failsCallingMethodOnNonexistentInterface() {
        try {
            fixture.proxy!!.callMethodOnNonexistentInterface()
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
        fixture.proxy!!.emitTwoSimpleSignals()

        assertTrue(waitUntil(fixture.proxy!!.gotSimpleSignal), "Got simple signal")
        assertTrue(waitUntil(fixture.proxy!!.gotSignalWithMap), "Got signal with map")
    }

    @Test
    fun canAccessAssociatedMethodCallMessageInMethodCallHandler() {
        // This will save pointer to method call message on server side
        fixture.proxy!!.doOperation(10u)

        assertNotNull(fixture.adaptor!!.methodCallMsg)
        assertEquals("doOperation", fixture.adaptor!!.methodName?.value)
    }

    @Test
    fun canAccessAssociatedMethodCallMessageInAsyncMethodCallHandler(): Unit = runTest {
        // This will save pointer to method call message on server side
        fixture.proxy!!.doOperationAsync(10u)

        assertNotNull(fixture.adaptor!!.methodCallMsg)
        assertEquals("doOperationAsync", fixture.adaptor?.methodName?.value)
    }

    @Test
    fun canSetGeneralMethodTimeoutWithLibsystemdVersionGreaterThan239() {
        globalAdaptorConnection.setMethodCallTimeout(5.seconds)
        assertEquals(5000.milliseconds, globalAdaptorConnection.getMethodCallTimeout())
    }

    @Test
    fun canCallMethodSynchronouslyWithoutAnEventLoopThread() {
        val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH, dontRunEventLoopThread = true)

        val multiplyRes = proxy.multiply(INT64_VALUE, DOUBLE_VALUE)

        assertEquals(INT64_VALUE * DOUBLE_VALUE, multiplyRes)
    }

    @Test
    fun canRegisterAdditionalVTableDynamicallyAtAnyTime() {
        val obj = fixture.adaptor!!.obj
        val interfaceName = InterfaceName("org.sdbuscpp.integrationtests2")
        val vtableSlot = obj.addVTable(interfaceName) {
            method(MethodName("add")) {
                call { a: Long, b: Double ->
                    a + b
                }
            }
            method(MethodName("subtract")) {
                call { a: Int, b: Int ->
                    a - b
                }
            }
        }

        // The new remote vtable is registered as long as we keep vtableSlot, so remote method calls now should pass
        val proxy = com.monkopedia.sdbus.createProxy(
            SERVICE_NAME,
            OBJECT_PATH,
            dontRunEventLoopThread = true
        )
        val result: Int =
            proxy.callMethod(interfaceName, MethodName("subtract")) { call(10, 2) }

        assertEquals(8, result)
        vtableSlot.release()
    }

    @Test
    fun canUnregisterAdditionallyRegisteredVTableAtAnyTime() {
        val obj = fixture.adaptor!!.obj
        val interfaceName = InterfaceName("org.sdbuscpp.integrationtests2")

        val slot = obj.addVTable(interfaceName) {
            method(MethodName("add")) {
                call { a: Long, b: Double ->
                    a + b
                }
            }
            method(MethodName("subtract")) {
                call { a: Int, b: Int ->
                    a - b
                }
            }
        }
        slot.release()

        // No such remote D-Bus method under given interface exists anymore...
        val proxy = com.monkopedia.sdbus.createProxy(
            SERVICE_NAME,
            OBJECT_PATH,
            dontRunEventLoopThread = true
        )
        try {
            proxy.callMethod<Unit>(interfaceName, MethodName("subtract")) { call(10, 2) }
            fail("Method did not throw")
        } catch (t: Throwable) {
            // Expected
        }
    }
}
