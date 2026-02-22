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
@file:OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.deserialize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.test.runTest

class DBusAsyncMethodsTest : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    @Test
    fun handlesCorrectlyABulkOfParallelServerSideAsyncMethods(): Unit = runTest {
        val resultCount = atomic(0)
        val invoke = atomic(false)
        val startedCount = atomic(0)
        val call: suspend () -> Unit = {
            // Reuse the fixture-managed connection to avoid creating/destroying extra
            // per-thread bus connections while stress-testing async method behavior.
            val proxy = TestProxy(globalProxyConnection, SERVICE_NAME, OBJECT_PATH)
            try {
                startedCount.incrementAndGet()
                while (!invoke.value);

                var localResultCount = 0
                for (i in 0 until 500) {
                    val result = proxy.doOperationAsync(i.mod(2).toUInt())
                    if (result == i.mod(2).toUInt()) { // Correct return value?
                        localResultCount++
                    }
                }

                resultCount.addAndGet(localResultCount)
            } finally {
                proxy.proxy.release()
            }
        }
        val pool = newFixedThreadPoolContext(3, "test-pool")

        val invocations = List(3) {
            launch(pool) {
                call()
            }
        }
        while (startedCount.value != 3);
        invoke.value = true
        invocations.joinAll()

        assertEquals(1500, resultCount.value)
        pool.close()
    }

    @Test
    fun invokesMethodAsynchronouslyOnClientSideWithFutureOnBasicAPILevel(): Unit = runTest {
        val future = fixture.proxy!!.doOperationClientSideAsyncOnBasicAPILevel(100u)

        val returnValue = future.deserialize<UInt>()

        assertEquals(100u, returnValue)
    }
}
