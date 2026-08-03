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
import org.junit.Assume.assumeTrue

internal actual fun dbusmockGetenv(name: String): String? = System.getenv(name)

// A failed JUnit assumption aborts the test and Gradle records it as skipped (JUnit 4 is what
// kotlin("test") resolves to for this module; org.junit.Assume is its Assumptions equivalent).
internal actual fun skipTest(reason: String) = assumeTrue(reason, false)

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
    if (dbusmockGetenv("DBUS_SESSION_BUS_ADDRESS") == null) {
        return dbusmockUnavailable("no D-Bus session bus (DBUS_SESSION_BUS_ADDRESS is unset)")
    }

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
    val process = try {
        ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (e: Exception) {
        // python3 not on PATH, etc.
        return dbusmockUnavailable("could not start '$python' ($e)")
    }

    // python3 exists but dbusmock module is missing -> process exits ~immediately non-zero.
    if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
        return dbusmockUnavailable(
            "'$python -m dbusmock' exited immediately with status ${process.exitValue()}; " +
                "the dbusmock module is probably not installed"
        )
    }
    return DbusmockHandle(process)
}

internal actual fun busyWait(millis: Long) {
    Thread.sleep(millis)
}
