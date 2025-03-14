/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.deserialize
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import platform.posix.size_t

class DBusAsyncMethodsTest : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    @Test
    fun throwsTimeoutErrorWhenClientSideAsyncMethodTimesOut(): Unit = runTest {
        var start = Clock.System.now()
        try {
            val promise = CompletableDeferred<UInt>()
            fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
                println("Response $res $err ${Clock.System.now() - start}")
                if (err == null) {
                    promise.complete(res)
                } else {
                    promise.completeExceptionally(err)
                }
            }

            start = Clock.System.now()
            fixture.proxy!!.doOperationClientSideAsyncWithTimeout(
                1.microseconds,
                1.seconds.inWholeMilliseconds.toUInt()
            ); // The operation will take 1s, but the timeout is 1us, so we should time out
            promise.await()

            fail("Expected [com.monkopedia.sdbus.Error] exception")
        } catch (e: Error) {
            assertContains(
                listOf(
                    "org.freedesktop.DBus.Error.Timeout",
                    "org.freedesktop.DBus.Error.NoReply"
                ),
                e.name
            )
            assertContains(
                listOf("Connection timed out", "Method call timed out"),
                e.errorMessage
            )
            val measuredTimeout = Clock.System.now() - start
            assertTrue(
                measuredTimeout <= 50.milliseconds,
                "Expected $measuredTimeout to be less than 50 ms"
            )
        }
    }

    @Test
    fun runsServerSideAsynchoronousMethodAsynchronously(): Unit = runTest {
        val results = MutableSharedFlow<UInt>(replay = 3, extraBufferCapacity = 3)
        var invoke by atomic(false)
        var startedCount = MutableSharedFlow<Unit>()
        val call: suspend (UInt) -> Unit = { param ->
            val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH)
            startedCount.emit(Unit)
            while (!invoke) delay(1)
            val result = proxy.doOperationAsync(param)
            results.emit(result)
        }
        val pool = newFixedThreadPoolContext(3, "test-pool")

        val invocations = listOf(1500u, 1000u, 500u).map {
            launch(pool) {
                call(it)
            }
        }
        startedCount.take(3).collect()
        invoke = true
        invocations.joinAll()

        assertEquals(setOf(500u, 1000u, 1500u), results.take(3).toList().toSet())
        pool.close()
    }

    @Test
    fun handlesCorrectlyABulkOfParallelServerSideAsyncMethods(): Unit = runTest {
        var resultCount by atomic(0.convert<size_t>())
        var invoke by atomic(false)
        var startedCount by atomic(0)
        val call: suspend () -> Unit = {
            val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH)
            ++startedCount
            while (!invoke);

            var localResultCount = 0.convert<size_t>()
            for (i in 0 until 500) {
                val result = proxy.doOperationAsync(i.mod(2).toUInt())
                if (result == i.mod(2).toUInt()) { // Correct return value?
                    localResultCount++
                }
            }

            resultCount += localResultCount
        }
        val pool = newFixedThreadPoolContext(3, "test-pool")

        val invocations = List(3) {
            launch(pool) {
                call()
            }
        }
        while (startedCount != 3);
        invoke = true
        invocations.joinAll()

        assertEquals(1500.convert<size_t>(), resultCount)
        pool.close()
    }

    @Test
    fun invokesMethodAsynchronouslyOnClientSide(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            if (err == null) {
                promise.complete(res)
            } else {
                promise.completeExceptionally(err)
            }
        }

        fixture.proxy!!.doOperationClientSideAsync(100u)

        assertEquals(100u, promise.await())
    }

    @Test
    fun invokesMethodAsynchronouslyOnClientSideWithFuture(): Unit = runTest {
        val result = fixture.proxy!!.awaitOperationClientSideAsync(100u)

        assertEquals(100u, result)
    }

    @Test
    fun invokesMethodAsynchronouslyOnClientSideWithFutureOnBasicAPILevel(): Unit = runTest {
        val future = fixture.proxy!!.doOperationClientSideAsyncOnBasicAPILevel(100u)

        val returnValue = future.deserialize<UInt>()

        assertEquals(100u, returnValue)
    }

    @Test
    fun answersThatAsyncCallIsPendingIfItIsInProgress() {
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err -> }

        val call = fixture.proxy!!.doOperationClientSideAsync(1000u)

        assertTrue(call.isActive)
    }

    @Test
    fun cancelsPendingAsyncCallOnClientSide(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u)
        }
        val call = fixture.proxy!!.doOperationClientSideAsync(100u)

        call.cancel()

        assertNull(
            withTimeoutOrNull(300.milliseconds) {
                promise.await()
            }
        )
    }

    @Test
    fun cancelsPendingAsyncCallOnClientSideByDestroyingOwningSlot(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u)
        }

        fixture.proxy!!.doOperationClientSideAsync(100u).cancel()
        // Now the slot is destroyed, cancelling the async call

        assertNull(
            withTimeoutOrNull(300.milliseconds) {
                promise.await()
            }
        )
    }

    @Test
    fun answersThatAsyncCallIsNotPendingAfterItHasBeenCancelled() {
        val promise = CompletableDeferred<UInt>()
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u)
        }
        val call = fixture.proxy!!.doOperationClientSideAsync(100u)

        call.cancel()

        assertFalse(call.isActive)
    }

    @Test
    fun answersThatAsyncCallIsNotPendingAfterItHasBeenCompleted(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u)
        }

        val call = fixture.proxy!!.doOperationClientSideAsync(0u)
        promise.await() // Wait for the call to finish

        assertTrue(
            waitUntil({
                !call.isActive
            })
        )
    }

    @Test
    fun returnsNonnullErrorWhenAsynchronousMethodCallFails(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            if (err == null) {
                promise.complete(res)
            } else {
                promise.completeExceptionally(err)
            }
        }
        fixture.proxy!!.doErroneousOperationClientSideAsync()

        try {
            promise.await()
            fail("Expected exception")
        } catch (t: Error) {
            // Expected failure
        }
    }

    @Test
    fun throwsErrorWhenClientSideAsynchronousMethodCallWithFutureFails(): Unit = runTest {
        try {
            fixture.proxy!!.awaitErroneousOperationClientSideAsync()
            fail("Expected exception")
        } catch (t: Error) {
            // Expected failure
        }
    }
}
