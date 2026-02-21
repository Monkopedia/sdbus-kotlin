package com.monkopedia.sdbus

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.ref.Cleaner
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(UnixFd.Companion::class)
actual class UnixFd actual constructor(val fd: Int, adoptFd: Unit) : Resource {
    private val state = OwnedFdState(fd)
    private val cleanable = CleanerHolder.cleaner.register(this, state)

    actual constructor(fd: Int) : this(JvmUnixFdSupport.checkedDup(fd), Unit)
    actual constructor(other: UnixFd) : this(JvmUnixFdSupport.checkedDup(other.fd), Unit)

    actual override fun release() {
        state.run()
        cleanable.clean()
    }

    actual companion object : KSerializer<UnixFd> {
        actual const val SERIAL_NAME: String = "sdbus.UnixFD"

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.INT)

        override fun deserialize(decoder: Decoder): UnixFd =
            decoder.decodeInline(descriptor).decodeInt().let(::UnixFd)

        override fun serialize(encoder: Encoder, value: UnixFd): Unit =
            encoder.encodeInline(descriptor).encodeInt(value.fd)
    }
}

private object CleanerHolder {
    val cleaner: Cleaner = Cleaner.create()
}

private class OwnedFdState(private val fd: Int) : Runnable {
    private val released = AtomicBoolean(false)

    override fun run() {
        if (fd < 0 || !released.compareAndSet(false, true)) return
        JvmUnixFdSupport.closeFd(fd)
    }
}

internal object JvmUnixFdSupport {
    private val nativeSupport: NativeUnixSocketSupport? = NativeUnixSocketSupport.createOrNull()

    val supportsFdDuplicationSemantics: Boolean
        get() = nativeSupport != null

    fun checkedDup(fd: Int): Int {
        if (fd < 0) return fd
        val support = nativeSupport ?: return fd
        return runCatching { support.duplicate(fd) }
            .getOrElse { throw createError(-1, "dup failed: ${it.message ?: "unknown"}") }
    }

    fun closeFd(fd: Int): Boolean {
        if (fd < 0) return true
        val support = nativeSupport ?: return false
        return runCatching {
            support.close(fd)
            true
        }.getOrElse { false }
    }

    fun createPipePair(): Pair<Int, Int>? {
        val support = nativeSupport ?: return null
        return runCatching { support.createPipePair() }
            .recoverCatching { support.createFallbackPair() }
            .getOrNull()
    }
}

private class NativeUnixSocketSupport private constructor(
    private val initFd: Method,
    private val duplicate: Method,
    private val close: Method,
    private val getFd: Method,
    private val initPipe: Method?
) {
    fun duplicate(fd: Int): Int {
        val source = descriptorFromFd(fd)
        val target = FileDescriptor()
        val duplicateDescriptor = (
            invokeStatic(duplicate, source, target) as? FileDescriptor
            ) ?: target
        val duplicateFd = extractFd(duplicateDescriptor)
        check(duplicateFd >= 0) { "dup returned invalid descriptor" }
        return duplicateFd
    }

    fun close(fd: Int) {
        invokeStatic(close, descriptorFromFd(fd))
    }

    fun createPipePair(): Pair<Int, Int> {
        val pipeMethod = checkNotNull(initPipe) { "initPipe unavailable" }
        val readFd = FileDescriptor()
        val writeFd = FileDescriptor()
        val initialized = invokeStatic(pipeMethod, readFd, writeFd, false) as? Boolean ?: false
        check(initialized) { "initPipe failed" }
        return extractFd(readFd) to extractFd(writeFd)
    }

    fun createFallbackPair(): Pair<Int, Int> {
        val temp = Files.createTempFile("sdbus-jvm-fd-", ".tmp").toFile().also {
            it.deleteOnExit()
        }
        FileInputStream(temp).use { readStream ->
            FileOutputStream(temp, true).use { writeStream ->
                val readFd = duplicate(extractFd(readStream.fd))
                return try {
                    val writeFd = duplicate(extractFd(writeStream.fd))
                    readFd to writeFd
                } catch (error: Throwable) {
                    close(readFd)
                    throw error
                }
            }
        }
    }

    private fun descriptorFromFd(fd: Int): FileDescriptor = FileDescriptor().also {
        invokeStatic(initFd, it, fd)
    }

    private fun extractFd(fileDescriptor: FileDescriptor): Int =
        (invokeStatic(getFd, fileDescriptor) as Number).toInt()

    companion object {
        fun createOrNull(): NativeUnixSocketSupport? = runCatching {
            val nativeUnixSocketClass = Class.forName("org.newsclub.net.unix.NativeUnixSocket")
            val ensureSupported = nativeUnixSocketClass.getDeclaredMethod("ensureSupported")
                .apply { isAccessible = true }
            invokeStatic(ensureSupported)

            NativeUnixSocketSupport(
                initFd = nativeUnixSocketClass
                    .getDeclaredMethod(
                        "initFD",
                        FileDescriptor::class.java,
                        Int::class.javaPrimitiveType!!
                    ).apply { isAccessible = true },
                duplicate = nativeUnixSocketClass
                    .getDeclaredMethod(
                        "duplicate",
                        FileDescriptor::class.java,
                        FileDescriptor::class.java
                    ).apply { isAccessible = true },
                close = nativeUnixSocketClass
                    .getDeclaredMethod("close", FileDescriptor::class.java)
                    .apply { isAccessible = true },
                getFd = nativeUnixSocketClass
                    .getDeclaredMethod("getFD", FileDescriptor::class.java)
                    .apply { isAccessible = true },
                initPipe = runCatching {
                    nativeUnixSocketClass.getDeclaredMethod(
                        "initPipe",
                        FileDescriptor::class.java,
                        FileDescriptor::class.java,
                        Boolean::class.javaPrimitiveType!!
                    ).apply { isAccessible = true }
                }.getOrNull()
            )
        }.getOrNull()
    }
}

private fun invokeStatic(method: Method, vararg args: Any?): Any? = try {
    method.invoke(null, *args)
} catch (error: InvocationTargetException) {
    throw error.targetException ?: error
}
