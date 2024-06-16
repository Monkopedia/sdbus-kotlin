@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.deserialize
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.header.with_future
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
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import platform.posix.size_t

class DBusAsyncMethodsTest : BaseTest() {
    private val fixture = TestFixtureSdBusCppLoop(this)


    @Test
    fun ThrowsTimeoutErrorWhenClientSideAsyncMethodTimesOut(): Unit = runTest {
        memScoped {
            var start = Clock.System.now()
            try {
                val promise = CompletableDeferred<UInt>()
                fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
                    println("Response $res $err")
                    if (err == null) promise.complete(res);
                    else promise.completeExceptionally(err);
                }

                start = Clock.System.now()
                fixture.m_proxy!!.doOperationClientSideAsyncWithTimeout(
                    1.microseconds,
                    1.seconds.inWholeMilliseconds.toUInt()
                ); // The operation will take 1s, but the timeout is 1us, so we should time out
                promise.await()

                fail("Expected sdbus::Error exception")
            } catch (e: Error) {
                assertContains(
                    listOf(
                        "org.freedesktop.DBus.Error.Timeout",
                        "org.freedesktop.DBus.Error.NoReply"
                    ),
                    e.name,
                );
                assertContains(
                    listOf("Connection timed out", "Method call timed out"),
                    e.errorMessage,
                );
                val measuredTimeout = Clock.System.now() - start
                assertTrue(measuredTimeout <= 50.milliseconds, "Expected $measuredTimeout to be less than 50 ms")
            }
        }
    }

    @Test
    fun RunsServerSideAsynchoronousMethodAsynchronously(): Unit = runTest {
        memScoped {
            // Yeah, this is kinda timing-dependent test, but times should be safe...
            val mtx = Mutex()
            val results = mutableListOf<UInt>()
            var invoke by atomic(false)
            var startedCount by atomic(0)
            val call: suspend AutofreeScope.(UInt) -> Unit = { param ->
                val proxy = TestProxy(this, SERVICE_NAME, OBJECT_PATH);
                ++startedCount;
                while (!invoke);
                val result = proxy.doOperationAsync(param);
                mtx.withLock {
                    results.add(result);
                }
            }
            val pool = newFixedThreadPoolContext(3, "test-pool")

            val invocations = listOf(1500u, 1000u, 500u).map {
                launch(pool) {
                    memScoped {
                        call(it)
                    }
                }
            }
            while (startedCount != 3);
            invoke = true;
            invocations.joinAll()

            assertEquals(setOf(500u, 1000u, 1500u), results.toSet())
            pool.close()
        }
    }

    @Test
    fun HandlesCorrectlyABulkOfParallelServerSideAsyncMethods(): Unit = runTest {

        memScoped {
            var resultCount by atomic(0.convert<size_t>());
            var invoke by atomic(false)
            var startedCount by atomic(0);
            val call: suspend AutofreeScope.() -> Unit = {
                val proxy = TestProxy(this, SERVICE_NAME, OBJECT_PATH);
                ++startedCount;
                while (!invoke);

                var localResultCount = 0.convert<size_t>();
                for (i in 0 until 500) {
                    val result = proxy.doOperationAsync(i.mod(2).toUInt());
                    if (result == i.mod(2).toUInt()) { // Correct return value?
                        localResultCount++;
                    }
                }

                resultCount += localResultCount;
            };
            val pool = newFixedThreadPoolContext(3, "test-pool")

            val invocations = List(3) {
                launch(pool) {
                    memScoped { call() }
                }
            }
            while (startedCount != 3);
            invoke = true;
            invocations.joinAll()

            assertEquals(1500.convert<size_t>(), resultCount);
            pool.close()
        }
    }

    @Test
    fun InvokesMethodAsynchronouslyOnClientSide(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            if (err == null) promise.complete(res)
            else promise.completeExceptionally(err)
        }

        fixture.m_proxy!!.doOperationClientSideAsync(100u);

        assertEquals(100u, promise.await());
    }

    @Test
    fun InvokesMethodAsynchronouslyOnClientSideWithFuture(): Unit = runTest {
        val result = fixture.m_proxy!!.doOperationClientSideAsync(100u, with_future);

        assertEquals(100u, result);
    }

    @Test
    fun InvokesMethodAsynchronouslyOnClientSideWithFutureOnBasicAPILevel(): Unit = runTest {
        memScoped {
            val future = fixture.m_proxy!!.doOperationClientSideAsyncOnBasicAPILevel(100u)

            val returnValue = future.deserialize<UInt>()

            assertEquals(100u, returnValue);
        }
    }

    @Test
    fun AnswersThatAsyncCallIsPendingIfItIsInProgress(): Unit = memScoped {
        fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err -> }

        val call = fixture.m_proxy!!.doOperationClientSideAsync(1000u);

        assertTrue(call.isPending());
    }

    @Test
    fun CancelsPendingAsyncCallOnClientSide(): Unit = runTest {
        memScoped {
            val promise = CompletableDeferred<UInt>()
            fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
                promise.complete(1u);
            }
            val call = fixture.m_proxy!!.doOperationClientSideAsync(100u);

            call.cancel();

            assertNull(
                withTimeoutOrNull(300.milliseconds) {
                    promise.await()
                }
            )
        }
    }

    @Test
    fun CancelsPendingAsyncCallOnClientSideByDestroyingOwningSlot(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u);
        }

        memScoped {
            fixture.m_proxy!!.doOperationClientSideAsync(100u, return_slot).own(this)
            // Now the slot is destroyed, cancelling the async call
        }

        assertNull(
            withTimeoutOrNull(300.milliseconds) {
                promise.await()
            }
        )
    }

    @Test
    fun AnswersThatAsyncCallIsNotPendingAfterItHasBeenCancelled(): Unit = memScoped {
        val promise = CompletableDeferred<UInt>()
        fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u);
        }
        val call = fixture.m_proxy!!.doOperationClientSideAsync(100u);

        call.cancel();

        assertFalse(call.isPending());
    }

    @Test
    fun AnswersThatAsyncCallIsNotPendingAfterItHasBeenCompleted(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            promise.complete(1u);
        }

        val call = fixture.m_proxy!!.doOperationClientSideAsync(0u);
        promise.await() // Wait for the call to finish

        assertTrue(waitUntil({ !call.isPending(); }))
    }

//    @Test
//    fun AnswersThatDefaultConstructedAsyncCallIsNotPending(): Unit = memScoped {
//        sdbus::PendingAsyncCall call;
//
//        assertFalse(call.isPending());
//    }

    @Test
    fun ReturnsNonnullErrorWhenAsynchronousMethodCallFails(): Unit = runTest {
        val promise = CompletableDeferred<UInt>()
        fixture.m_proxy!!.installDoOperationClientSideAsyncReplyHandler { res, err ->
            if (err == null) promise.complete(res);
            else promise.completeExceptionally(err);
        }
        fixture.m_proxy!!.doErroneousOperationClientSideAsync();

        try {
            promise.await()
            fail("Expected exception")
        } catch (t: Error) {
            // Expected failure
        }
    }

    @Test
    fun ThrowsErrorWhenClientSideAsynchronousMethodCallWithFutureFails(): Unit = runTest {
        try {
            fixture.m_proxy!!.doErroneousOperationClientSideAsync(with_future);
            fail("Expected exception")
        } catch (t: Error) {
            // Expected failure
        }
    }


}
