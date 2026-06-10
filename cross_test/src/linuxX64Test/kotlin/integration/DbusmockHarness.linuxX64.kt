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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.SIGTERM
import platform.posix.WNOHANG
import platform.posix._exit
import platform.posix.execvp
import platform.posix.fork
import platform.posix.getenv
import platform.posix.kill
import platform.posix.usleep
import platform.posix.waitpid

internal actual fun dbusmockGetenv(name: String): String? = getenv(name)?.toKString()

internal actual class DbusmockHandle(private val pid: Int) {
    actual fun stop() {
        kill(pid, SIGTERM)
        // Reap the child so it does not linger as a zombie. Give it a short grace period.
        memScoped {
            val status = alloc<IntVar>()
            repeat(50) {
                if (waitpid(pid, status.ptr, WNOHANG) != 0) return
                usleep(10_000u)
            }
        }
    }
}

internal actual fun launchDbusmock(
    busName: String,
    objectPath: String,
    interfaceName: String
): DbusmockHandle? {
    // No session bus -> nothing to launch dbusmock on; skip.
    if (dbusmockGetenv("DBUS_SESSION_BUS_ADDRESS") == null) return null

    val python = dbusmockGetenv("DBUSMOCK_PYTHON") ?: "python3"
    val args = listOf(
        python,
        "-m",
        "dbusmock",
        "--session",
        busName,
        objectPath,
        interfaceName
    )

    val pid = fork()
    if (pid < 0) return null
    if (pid == 0) {
        // Child: exec python3 -m dbusmock. On any failure, exit non-zero.
        memScoped {
            val cArgs = args.map { it.cstr.getPointer(this) } + listOf(null)
            execvp(python, allocArrayOf(cArgs))
            // execvp only returns on failure (e.g. python3 not on PATH).
            _exit(127)
        }
    }

    // Parent: detect an immediate failure (e.g. dbusmock module missing -> python exits fast).
    memScoped {
        val status = alloc<IntVar>()
        repeat(50) {
            if (waitpid(pid, status.ptr, WNOHANG) == pid) {
                // Child already exited: dbusmock not installed / failed to start. Skip.
                return null
            }
            usleep(10_000u)
        }
    }
    return DbusmockHandle(pid)
}

internal actual fun busyWait(millis: Long) {
    usleep((millis * 1_000L).toUInt())
}
