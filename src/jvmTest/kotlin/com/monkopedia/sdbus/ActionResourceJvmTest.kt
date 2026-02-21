package com.monkopedia.sdbus

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionResourceJvmTest {
    @Test
    fun release_runsActionOnceWhenCalledConcurrently() {
        val runCount = AtomicInteger(0)
        val resource = ActionResource {
            runCount.incrementAndGet()
        }
        val workers = 8
        val attemptsPerWorker = 50
        val executor = Executors.newFixedThreadPool(workers)
        val done = CountDownLatch(workers)

        repeat(workers) {
            executor.submit {
                repeat(attemptsPerWorker) {
                    resource.release()
                }
                done.countDown()
            }
        }

        assertTrue(done.await(2, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(1, runCount.get())
    }
}
