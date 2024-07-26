package com.monkopedia.sdbus

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
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
 * an explicitly provided fd by either duplicating or adopting that fd as-is.
 *
 */
@OptIn(ExperimentalNativeApi::class)
@Serializable(UnixFd.Companion::class)
actual class UnixFd actual constructor(val fd: Int, adoptFd: Unit) : Resource {
    private var wasReleased = false
    private val cleaner = createCleaner(fd) {
        if (it >= 0) {
            close(it)
        }
    }

    actual constructor(fd: Int) : this(checkedDup(fd), Unit)
    actual constructor(other: UnixFd) : this(checkedDup(other.fd), Unit)

    val isValid: Boolean
        get() = fd >= 0 && !wasReleased

    actual override fun release() {
        close(fd)
        wasReleased = true
    }

    actual companion object : KSerializer<UnixFd> {

        actual const val SERIAL_NAME = "sdbus.UnixFD"

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
