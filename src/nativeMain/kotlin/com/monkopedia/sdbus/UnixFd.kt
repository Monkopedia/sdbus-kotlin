/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
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
package com.monkopedia.sdbus

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.atomicfu.atomic
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind.INT
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import platform.posix.close
import platform.posix.dup
import platform.posix.errno

/**
 * @struct UnixFd
 *
 * UnixFd is a representation of file descriptor D-Bus type that owns
 * the underlying fd, provides access to it, and closes the fd when
 * the UnixFd goes out of scope.
 *
 * UnixFd can be default constructed (owning invalid fd), or constructed from
 * an explicitly provided fd by either duplicating ([UnixFd] primary constructor)
 * or adopting that fd as-is ([UnixFd.adopt]).
 *
 */
@OptIn(ExperimentalNativeApi::class)
@Serializable(UnixFd.Companion::class)
actual class UnixFd internal actual constructor(
    /** The underlying file descriptor owned by this instance, or a negative value if invalid. */
    val fd: Int,
    adoptFd: Unit
) : Resource {
    private var wasReleased = false
    private val resource = fd to singleCall<Int> {
        if (it >= 0) {
            close(it)
        }
    }
    private val cleaner = createCleaner(resource) { (fd, closeOnce) ->
        closeOnce(fd)
    }

    actual constructor(fd: Int) : this(checkedDup(fd), Unit)
    actual constructor(other: UnixFd) : this(checkedDup(other.fd), Unit)

    /** Whether this instance owns a valid, not-yet-released file descriptor. */
    val isValid: Boolean
        get() = fd >= 0 && !wasReleased

    actual override fun release() {
        resource.second(resource.first)
        wasReleased = true
    }

    /**
     * Relinquishes ownership of the underlying fd without closing it.
     *
     * Used when the fd's ownership is transferred elsewhere (e.g. adopted by
     * a connection); disarms both [release] and the GC cleaner.
     */
    internal fun detach() {
        // Consume the single-shot closer with an invalid fd so neither
        // release() nor the cleaner will close the real descriptor.
        resource.second(-1)
        wasReleased = true
    }

    actual companion object : KSerializer<UnixFd> {

        actual const val SERIAL_NAME = "sdbus.UnixFD"

        /**
         * Adopts [fd] as-is, taking over its ownership without duplicating it.
         */
        actual fun adopt(fd: Int): UnixFd = UnixFd(fd, Unit)

        private fun checkedDup(fd: Int): Int {
            if (fd < 0) {
                return fd
            }
            return dup(fd).also {
                if (it < 0) {
                    throw createError(errno, "dup failed")
                }
            }
        }

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(SERIAL_NAME, INT)

        override fun deserialize(decoder: Decoder): UnixFd =
            decoder.decodeInline(descriptor).decodeInt().let(::UnixFd)

        override fun serialize(encoder: Encoder, value: UnixFd) {
            encoder.encodeInline(descriptor).encodeInt(value.fd)
        }
    }
}

private fun <T> singleCall(callback: (T) -> Unit): (T) -> Unit {
    return object : (T) -> Unit {
        private val called = atomic(false)
        override fun invoke(p1: T) {
            if (!called.compareAndSet(false, true)) return
            callback(p1)
        }
    }
}
