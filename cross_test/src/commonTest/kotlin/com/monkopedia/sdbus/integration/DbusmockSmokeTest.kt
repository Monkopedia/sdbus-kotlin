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
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.getProperty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Smoke test for the python-dbusmock independent-peer harness ([DbusmockHarness]).
 *
 * This proves the end-to-end loop on whichever backend the test compiles for (JVM wire
 * via `jvmTest`, native sd-bus via `linuxX64Test`): our client scripts a generic dbusmock
 * object, then calls a method and reads a property on it and asserts the results round-trip
 * through the foreign (Python/GDBus) serializer.
 *
 * Deeper coverage (signals, property writes, richer payloads, real templates such as BlueZ /
 * Secret Service) lands in the follow-up tickets that this harness unblocks.
 *
 * Skips cleanly when python3 / python3-dbusmock is not installed. See [DbusmockHarness] for
 * installation instructions.
 */
class DbusmockSmokeTest {
    @Test
    fun callsMethodAndReadsPropertyOnDbusmockPeer() = runBlocking {
        val suffix = Random.nextInt(100_000, 999_999)
        val busName = "com.monkopedia.sdbus.dbusmock.Smoke$suffix"
        val objectPath = "/com/monkopedia/sdbus/dbusmock/Smoke$suffix"
        val interfaceName = "com.monkopedia.sdbus.dbusmock.Smoke$suffix"

        val handle = launchDbusmock(busName, objectPath, interfaceName)
        if (handle == null) {
            // python3 / python3-dbusmock not available, or no session bus — skip cleanly.
            println(
                "[DbusmockSmokeTest] SKIP: python-dbusmock unavailable. " +
                    "Install via 'apt install python3-dbusmock' / 'pip install python-dbusmock' " +
                    "(see DbusmockHarness KDoc)."
            )
            return@runBlocking
        }

        val connection = createBusConnection()
        connection.startEventLoop()
        val mockControl = createProxy(connection, ServiceName(busName), ObjectPath(objectPath))
        val proxy = createProxy(connection, ServiceName(busName), ObjectPath(objectPath))

        try {
            // dbusmock takes a moment to claim its bus name; retry the first control call until
            // the name is owned (or we time out).
            retry(timeoutMillis = 15_000) {
                addMethod(
                    mockControl,
                    interfaceName,
                    name = "Increment",
                    inSig = "i",
                    outSig = "i",
                    code = "ret = args[0] + 1"
                )
            }
            addProperty(
                mockControl,
                interfaceName,
                name = "Greeting",
                value = Variant("hello-from-dbusmock")
            )

            // Method call: our client -> dbusmock (foreign serializer) -> back.
            val incremented = proxy.callMethod<Int>(
                InterfaceName(interfaceName),
                MethodName("Increment")
            ) {
                call(41)
            }
            assertEquals(42, incremented, "Increment(41) via dbusmock should return 42")

            // Property read through the standard org.freedesktop.DBus.Properties.Get path.
            val greeting = proxy.getProperty<String>(
                InterfaceName(interfaceName),
                PropertyName("Greeting")
            )
            assertEquals("hello-from-dbusmock", greeting, "Greeting property via dbusmock mismatch")
        } finally {
            proxy.release()
            mockControl.release()
            connection.stopEventLoop()
            connection.release()
            handle.stop()
        }
    }

    private fun addMethod(
        mockControl: com.monkopedia.sdbus.Proxy,
        interfaceName: String,
        name: String,
        inSig: String,
        outSig: String,
        code: String
    ) {
        mockControl.callMethod<Unit>(MOCK_INTERFACE, MethodName("AddMethod")) {
            call(interfaceName, name, inSig, outSig, code)
        }
    }

    private fun addProperty(
        mockControl: com.monkopedia.sdbus.Proxy,
        interfaceName: String,
        name: String,
        value: Variant
    ) {
        mockControl.callMethod<Unit>(MOCK_INTERFACE, MethodName("AddProperty")) {
            call(interfaceName, name, value)
        }
    }

    private inline fun retry(timeoutMillis: Long, block: () -> Unit) {
        val start = kotlin.time.TimeSource.Monotonic.markNow()
        var last: Throwable? = null
        while (start.elapsedNow().inWholeMilliseconds < timeoutMillis) {
            val result = runCatching(block)
            if (result.isSuccess) return
            last = result.exceptionOrNull()
            busyWait(100)
        }
        throw AssertionError("Timed out waiting for dbusmock to become ready", last)
    }

    private companion object {
        private val MOCK_INTERFACE = InterfaceName("org.freedesktop.DBus.Mock")
    }
}

/** Platform busy-wait used to space out readiness retries without a coroutine delay. */
internal expect fun busyWait(millis: Long)
