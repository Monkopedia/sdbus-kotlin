/**
 *
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

package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Regression test for the native event-loop thread-starvation bug.
 *
 * Each [Connection]'s event loop runs a BLOCKING `poll()` for the loop's whole lifetime. Before the
 * fix every loop was launched onto a single shared, process-wide fixed thread pool of 8 threads
 * (`newFixedThreadPoolContext(8, "EventThreads")`). A running loop occupies its pool thread for as
 * long as it lives, so AT MOST 8 connection loops can run at once: with more than 8 live loops, the
 * 9th+ coroutine is QUEUED and never actually runs.
 *
 * The user-visible symptom (and exactly what wave #114 will multiply by auto-starting a loop in
 * `createObject`) is that a served object whose connection loop never runs can never process an
 * incoming call. This test stands up [SERVERS] (> 8) independent served objects, each on its own
 * connection with its own event loop, and calls every one of them synchronously. On the buggy code
 * the calls to the servers past the 8-thread boundary block until they time out (their loops never
 * run); with the per-connection dedicated dispatcher they all answer promptly. It also asserts each
 * connection releases promptly afterwards.
 *
 * Needs a real D-Bus session bus — run under `dbus-run-session`.
 */
class EventLoopConcurrencyStressTest {

    private val servers = 16
    private val iface = InterfaceName("org.sdbuskotlin.loopstress.Iface")
    private val path = ObjectPath("/org/sdbuskotlin/loopstress")
    private val echo = MethodName("Echo")
    private val callTimeout = 5.seconds

    @Test
    fun moreThanEightConcurrentLoopsAllServeAndReleasePromptly() {
        val connections = ArrayList<Connection>(servers)
        val vtables = ArrayList<Resource>(servers)
        val objects = ArrayList<Object>(servers)
        try {
            // Stand up `servers` independent served objects, each with its own connection + loop.
            repeat(servers) { index ->
                val name = ServiceName("org.sdbuskotlin.loopstress.Server$index")
                val conn = createBusConnection(name)
                val obj = createObject(conn, path)
                val vtable = obj.addVTable(iface) {
                    method(echo) {
                        call { value: Int -> value }
                    }
                }
                conn.startEventLoop()
                connections.add(conn)
                objects.add(obj)
                vtables.add(vtable)
            }

            // Call every server. With the 8-thread cap, servers >= 8 never run their loop, so these
            // calls block until `callTimeout` and then throw. With dedicated dispatchers, all answer.
            repeat(servers) { index ->
                val name = ServiceName("org.sdbuskotlin.loopstress.Server$index")
                val proxy = createProxy(name, path, runEventLoopThread = false)
                try {
                    val mark = TimeSource.Monotonic.markNow()
                    val result = proxy.callMethod<Int>(iface, echo) {
                        timeout = callTimeout
                        call(index)
                    }
                    val elapsed = mark.elapsedNow().inWholeMilliseconds
                    assertEquals(
                        index,
                        result,
                        "server $index returned wrong value"
                    )
                    assertTrue(
                        elapsed < 2_000,
                        "call to server $index took ${elapsed}ms — its event loop is starving on " +
                            "the shared 8-thread pool instead of running on its own thread."
                    )
                } finally {
                    proxy.release()
                }
            }
        } finally {
            objects.forEach { it.release() }
            vtables.forEach { it.release() }
            // Releasing a connection must stop its loop and free its resources promptly; the 2s
            // fail-safe in release() must never be hit.
            var maxReleaseMillis = 0L
            connections.forEach { conn ->
                val mark = TimeSource.Monotonic.markNow()
                conn.release()
                val elapsed = mark.elapsedNow().inWholeMilliseconds
                if (elapsed > maxReleaseMillis) maxReleaseMillis = elapsed
            }
            println("Max connection release = ${maxReleaseMillis}ms across $servers connections")
            assertTrue(
                maxReleaseMillis < 1_000,
                "A connection release took ${maxReleaseMillis}ms (~2s fail-safe) — a starved " +
                    "loop never observed its exit notification."
            )
        }
    }
}
