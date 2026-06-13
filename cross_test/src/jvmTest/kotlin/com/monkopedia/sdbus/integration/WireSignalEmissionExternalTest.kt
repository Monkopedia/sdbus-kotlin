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
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.signal
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Proves that a signal our object emits actually traverses the BUS and reaches an INDEPENDENT
 * out-of-process observer (epic #93 phase 3b) — the piece that was deliberately deferred from
 * phase 3. The observer is `dbus-monitor` (a separate process), so in-process delivery cannot mask
 * the result:
 *  - wire backend BEFORE this phase: emission was in-process only, so `dbus-monitor` never sees the
 *    signal and this test FAILS (the poll loop times out);
 *  - wire backend AFTER this phase: emission goes over the wire, so `dbus-monitor` captures it;
 *  - dbus-java backend: always emitted over the real wire, so it captures it too.
 *
 * JVM-only (cross_test `jvmTest`) because the owned-connection backend is JVM-only and toggle-gated;
 * the native backend's over-the-wire emission is already covered elsewhere. Skips cleanly when no
 * session bus or no `dbus-monitor` is available.
 */
class WireSignalEmissionExternalTest {

    @Test
    fun emittedSignal_reachesExternalDbusMonitor() = runBlocking {
        val sessionBus = System.getenv("DBUS_SESSION_BUS_ADDRESS")
        if (sessionBus == null) {
            println("[WireSignalEmissionExternalTest] SKIP: no DBUS_SESSION_BUS_ADDRESS.")
            return@runBlocking
        }
        val dbusMonitor = findExecutable("dbus-monitor")
        if (dbusMonitor == null) {
            println("[WireSignalEmissionExternalTest] SKIP: dbus-monitor not on PATH.")
            return@runBlocking
        }

        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.wireemit$id")
        val path = ObjectPath("/com/monkopedia/sdbus/wireemit$id")
        val iface = InterfaceName("com.monkopedia.sdbus.wireemit$id.Interface")
        val member = SignalName("Pinged")
        // A unique marker that only appears in the captured output if THIS signal's payload arrived
        // over the bus, not just a same-named message from some other source.
        val marker = 0x5d_b0_0000.toInt() or (id and 0xffff)

        val output = File.createTempFile("wireemit-monitor", ".txt").apply { deleteOnExit() }
        val monitor = ProcessBuilder(
            dbusMonitor.absolutePath,
            "--session",
            "type='signal',interface='${iface.value}',member='${member.value}'"
        ).redirectErrorStream(true)
            .redirectOutput(output)
            .start()

        val connection = createBusConnection(service)
        val obj = createObject(connection, path)
        val registration = obj.addVTable(iface) {
            signal(member) {
                with<Int>("value")
            }
        }
        connection.startEventLoop()

        try {
            // dbus-monitor installs its match asynchronously after launch; emit repeatedly until the
            // observer captures the marker (proving end-to-end bus delivery) or we time out. Bounded
            // so a missing emission FAILS fast rather than hanging.
            val deadline = System.currentTimeMillis() + 15_000
            var observed = false
            while (System.currentTimeMillis() < deadline) {
                obj.emitSignal(iface, member) { call(marker) }
                Thread.sleep(250)
                if (output.readText().contains(marker.toString())) {
                    observed = true
                    break
                }
            }
            assertTrue(
                observed,
                "external dbus-monitor never received the over-the-wire signal " +
                    "${iface.value}.${member.value} (marker=$marker)\n--- captured ---\n" +
                    output.readText()
            )
        } finally {
            registration.release()
            obj.release()
            connection.stopEventLoop()
            connection.release()
            monitor.destroy()
            if (!monitor.waitFor(5, TimeUnit.SECONDS)) monitor.destroyForcibly()
        }
    }

    private fun findExecutable(name: String): File? {
        val path = System.getenv("PATH") ?: return null
        return path.split(File.pathSeparator)
            .map { File(it, name) }
            .firstOrNull { it.canExecute() }
    }
}
