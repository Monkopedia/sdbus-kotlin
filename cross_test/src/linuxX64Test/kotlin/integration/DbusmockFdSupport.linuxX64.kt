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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.posix.close
import platform.posix.pipe
import platform.posix.read
import platform.posix.write

internal actual val peerStructMarshallingSupported: Boolean = true

@OptIn(ExperimentalForeignApi::class)
internal actual fun createTestPipe(): Pair<Int, Int>? = memScoped {
    val fds = allocArray<IntVar>(2)
    if (pipe(fds) != 0) return null
    fds[0] to fds[1]
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeToFd(fd: Int, data: ByteArray): Boolean = data.usePinned { pinned ->
    write(fd, pinned.addressOf(0), data.size.convert()).toLong() == data.size.toLong()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFromFd(fd: Int, maxBytes: Int): ByteArray? {
    val buffer = ByteArray(maxBytes)
    val count = buffer.usePinned { pinned ->
        read(fd, pinned.addressOf(0), maxBytes.convert()).toLong()
    }
    if (count < 0) return null
    return buffer.copyOf(count.toInt())
}

internal actual fun closeTestFd(fd: Int) {
    close(fd)
}

/** See the expect declaration (DbusmockForeignErrorTest.kt): sd-bus preserves foreign error names. */
internal actual val peerErrorNameMappingSupported: Boolean = true

/**
 * See the expect declaration (DbusmockSecretServiceTest.kt): the native backend deserializes
 * multi-out (grouped) replies from remote peers correctly.
 */
internal actual val peerGroupedReturnSupported: Boolean = true
