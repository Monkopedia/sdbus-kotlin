/**
 *
 * (C) 2024 - 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Event-loop symmetry coverage (issue #114): `createObject` auto-starts the connection's I/O event
 * loop (`runEventLoopThread` defaults to `true`, mirroring `createProxy`), and
 * `Connection.startEventLoop` is idempotent — calling it again (explicitly, after the auto-start)
 * must NOT spawn a second loop thread.
 *
 * The repeated starts are followed by a real signal round-trip: if a redundant start had spawned a
 * second loop racing the first on the same connection, the round-trip would hang or misbehave. A
 * clean delivery is the behavioural proof that exactly one loop is running on each side.
 *
 * Runs in commonTest so it exercises both the native sd-bus loop (`ConnectionImpl.startEventLoop`,
 * guarded by `asyncLoopThread != null || eventLoopStarting`) and the JVM wire backend (whose
 * `startEventLoop` is a no-op — trivially idempotent).
 */
class EventLoopStartIdempotencyTest {

    @Test
    fun createObjectAutoStartsLoop_andRepeatedStartEventLoopIsIdempotent() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val serviceName = ServiceName("com.monkopedia.sdbus.loopidem$id")
        val path = ObjectPath("/com/monkopedia/sdbus/loopidem$id")
        val iface = InterfaceName("com.monkopedia.sdbus.LoopIdem")

        val serverConnection = createBusConnection(serviceName)
        // createObject() auto-starts the server loop (default runEventLoopThread = true).
        val obj = createObject(serverConnection, path)
        // Redundant explicit starts: these must be idempotent no-ops, never a second loop thread.
        serverConnection.startEventLoop()
        serverConnection.startEventLoop()

        val registration = obj.addVTable(iface) {
            signal(SignalName("Ping")) {
                with<Int>("value")
            }
        }

        val proxyConnection = createBusConnection()
        // createProxy() likewise auto-starts; the extra explicit start must also be a no-op.
        val proxy = createProxy(proxyConnection, serviceName, path)
        proxyConnection.startEventLoop()

        val seen = CompletableDeferred<Int>()
        val handler = proxy.onSignal(iface, SignalName("Ping")) {
            call { value: Int -> seen.complete(value) }
        }

        try {
            obj.emitSignal(iface, SignalName("Ping")) {
                call(42)
            }
            assertEquals(42, withTimeout(5_000) { seen.await() })
        } finally {
            handler.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
