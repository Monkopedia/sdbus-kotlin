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
 * Struct marshalling to/from remote peers is supported since the issue #71 fix (structs are
 * decomposed into wire-shaped values via JvmValueCodec on the way to/from dbus-java).
 */
internal actual val peerStructMarshallingSupported: Boolean = true

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
 * Foreign error names/messages are preserved verbatim since the issue #72 fix (remote error
 * replies construct Error(wireName, wireMessage) instead of going through the errno mapping).
 */
internal actual val peerErrorNameMappingSupported: Boolean = true

/**
 * Multi-out (grouped) replies from remote peers are supported since the issue #74 fix
 * (structured deserialization consumes one reply value per out-arg via the common
 * degrouping machinery).
 */
internal actual val peerGroupedReturnSupported: Boolean = true
