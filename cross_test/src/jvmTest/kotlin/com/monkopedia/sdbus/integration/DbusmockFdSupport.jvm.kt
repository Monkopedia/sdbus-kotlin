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

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Struct marshalling to/from remote peers is supported since the issue #71 fix (structs are
 * decomposed into wire-shaped values via JvmValueCodec on the way to/from the wire marshaller).
 */
internal actual val peerStructMarshallingSupported: Boolean = true

/**
 * Closes #83: with the owned-connection (wire) JVM backend, raw fds round-trip natively via
 * junixsocket SCM_RIGHTS, so the cross-process unix-fd case runs against a real dbusmock peer. We
 * surface a real pipe through junixsocket's native primitives (the same path the library uses for
 * UnixFd, reached reflectively so no `--add-opens` is needed for java.base/java.io).
 *
 * The owned wire connection is now the only JVM backend (epic #93 phase 6 retired dbus-java), so
 * raw fds round-trip natively over SCM_RIGHTS via junixsocket — closing the #83 JVM fd wire gap.
 */
internal actual fun createTestPipe(): Pair<Int, Int>? = JunixFdBridge.createPipePair()

internal actual fun writeToFd(fd: Int, data: ByteArray): Boolean {
    // Write through a duplicate so the caller's original descriptor stays open.
    val dup = JunixFdBridge.duplicate(fd)
    return FileOutputStream(JunixFdBridge.descriptorFor(dup)).use {
        it.write(data)
        it.flush()
        true
    }
}

internal actual fun readFromFd(fd: Int, maxBytes: Int): ByteArray? {
    val dup = JunixFdBridge.duplicate(fd)
    return FileInputStream(JunixFdBridge.descriptorFor(dup)).use { stream ->
        val buffer = ByteArray(maxBytes)
        val read = stream.read(buffer)
        if (read < 0) ByteArray(0) else buffer.copyOf(read)
    }
}

internal actual fun closeTestFd(fd: Int) {
    JunixFdBridge.close(fd)
}

/**
 * Minimal reflective bridge onto junixsocket's `NativeUnixSocket` native primitives (pipe / dup /
 * close / fd<->FileDescriptor), used only by JVM cross_test fd plumbing. junixsocket is on the test
 * runtime classpath as a direct dependency (the owned JVM backend uses it).
 */
private object JunixFdBridge {
    private val initFd: Method
    private val getFd: Method
    private val closeFd: Method
    private val duplicateFd: Method
    private val initPipe: Method

    init {
        val type = Class.forName("org.newsclub.net.unix.NativeUnixSocket")
        type.getDeclaredMethod("ensureSupported").apply { isAccessible = true }.invoke(null)
        initFd = type.getDeclaredMethod(
            "initFD",
            FileDescriptor::class.java,
            Int::class.javaPrimitiveType!!
        ).apply { isAccessible = true }
        getFd = type.getDeclaredMethod("getFD", FileDescriptor::class.java)
            .apply { isAccessible = true }
        closeFd = type.getDeclaredMethod("close", FileDescriptor::class.java)
            .apply { isAccessible = true }
        duplicateFd = type.getDeclaredMethod(
            "duplicate",
            FileDescriptor::class.java,
            FileDescriptor::class.java
        ).apply { isAccessible = true }
        initPipe = type.getDeclaredMethod(
            "initPipe",
            FileDescriptor::class.java,
            FileDescriptor::class.java,
            Boolean::class.javaPrimitiveType!!
        ).apply { isAccessible = true }
    }

    fun createPipePair(): Pair<Int, Int>? = runCatching {
        val read = FileDescriptor()
        val write = FileDescriptor()
        invoke(initPipe, read, write, false)
        fdOf(read) to fdOf(write)
    }.getOrNull()

    fun descriptorFor(fd: Int): FileDescriptor = FileDescriptor().also { invoke(initFd, it, fd) }

    fun duplicate(fd: Int): Int {
        val target = FileDescriptor()
        val result = invoke(duplicateFd, descriptorFor(fd), target) as? FileDescriptor ?: target
        return fdOf(result)
    }

    fun close(fd: Int) {
        runCatching { invoke(closeFd, descriptorFor(fd)) }
    }

    private fun fdOf(descriptor: FileDescriptor): Int =
        (invoke(getFd, descriptor) as Number).toInt()

    private fun invoke(method: Method, vararg args: Any?): Any? = try {
        method.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }
}

/**
 * Foreign error names/messages are preserved verbatim since the issue #72 fix (remote error
 * replies construct SdbusException(wireName, wireMessage) instead of going through the errno mapping).
 */
internal actual val peerErrorNameMappingSupported: Boolean = true

/**
 * Multi-out (grouped) replies from remote peers are supported since the issue #74 fix
 * (structured deserialization consumes one reply value per out-arg via the common
 * degrouping machinery).
 */
internal actual val peerGroupedReturnSupported: Boolean = true
