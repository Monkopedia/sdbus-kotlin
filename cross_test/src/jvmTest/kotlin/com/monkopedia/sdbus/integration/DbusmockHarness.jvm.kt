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

import java.util.concurrent.TimeUnit

internal actual fun dbusmockGetenv(name: String): String? = System.getenv(name)

internal actual class DbusmockHandle(private val process: Process) {
    actual fun stop() {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }
}

internal actual fun launchDbusmock(
    busName: String,
    objectPath: String,
    interfaceName: String,
    objectManager: Boolean,
    template: String?
): DbusmockHandle? {
    // No session bus -> nothing to launch dbusmock on; skip.
    if (dbusmockGetenv("DBUS_SESSION_BUS_ADDRESS") == null) return null

    val python = dbusmockGetenv("DBUSMOCK_PYTHON") ?: "python3"
    val command = buildList {
        add(python)
        add("-m")
        add("dbusmock")
        // Explicit --session overrides any SYSTEM_BUS flag a template declares (e.g. bluez5).
        add("--session")
        if (template != null) {
            add("-t")
            add(template)
        } else {
            if (objectManager) add("-m")
            add(busName)
            add(objectPath)
            add(interfaceName)
        }
    }
    return try {
        val process = ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        // python3 exists but dbusmock module is missing -> process exits ~immediately non-zero.
        if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
            // Exited already: dbusmock not installed / failed to start. Skip.
            return null
        }
        DbusmockHandle(process)
    } catch (_: Exception) {
        // python3 not on PATH, etc. Skip.
        null
    }
}

internal actual fun busyWait(millis: Long) {
    Thread.sleep(millis)
}
