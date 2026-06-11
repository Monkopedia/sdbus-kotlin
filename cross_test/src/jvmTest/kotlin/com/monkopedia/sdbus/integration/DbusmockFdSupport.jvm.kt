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

/**
 * See the expect declaration: the JVM backend cannot yet marshal custom @Serializable struct
 * values to or from remote peers, so the remote-struct sub-cases are skipped on this backend.
 */
internal actual val peerStructMarshallingSupported: Boolean = false

/**
 * The JVM has no portable way to surface a freshly created pipe as a raw integer file
 * descriptor from test code (java.io.FileDescriptor's int field is sealed behind JPMS),
 * so the unix-fd sub-case is exercised on the native backend only and skipped here.
 */
internal actual fun createTestPipe(): Pair<Int, Int>? = null

internal actual fun writeToFd(fd: Int, data: ByteArray): Boolean =
    throw UnsupportedOperationException("raw fds are not accessible on the JVM test backend")

internal actual fun readFromFd(fd: Int, maxBytes: Int): ByteArray? =
    throw UnsupportedOperationException("raw fds are not accessible on the JVM test backend")

internal actual fun closeTestFd(fd: Int) {
    // No raw fds are ever created on the JVM backend; nothing to close.
}

/**
 * See the expect declaration (DbusmockForeignErrorTest.kt): the JVM backend discards foreign
 * error names (issue #72). Flip to `true` when that bug is fixed.
 */
internal actual val peerErrorNameMappingSupported: Boolean = false

/**
 * See the expect declaration (DbusmockSecretServiceTest.kt): the JVM backend cannot
 * deserialize multi-out (grouped) method replies from a real remote peer (issue #74). Flip
 * to `true` when that bug is fixed.
 */
internal actual val peerGroupedReturnSupported: Boolean = false
