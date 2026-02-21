/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
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
@file:Suppress("ktlint:standard:function-literal")
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createDirectBusConnection
import com.monkopedia.sdbus.createError
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.createServerBus
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.linux.sockaddr_un
import platform.posix.AF_UNIX
import platform.posix.F_SETFD
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.fcntl
import platform.posix.getenv
import platform.posix.listen
import platform.posix.memset
import platform.posix.pipe
import platform.posix.read
import platform.posix.sa_family_tVar
import platform.posix.snprintf
import platform.posix.socket
import platform.posix.umask
import platform.posix.unlink
import platform.posix.usleep
import platform.posix.write

class NativeInteropPeerTest {
    @Test
    fun jvmClientCanInvokeIncrementOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val methodName = requireEnv("KDBUS_INTEROP_METHOD")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedArg = requireEnv("KDBUS_INTEROP_EXPECTED_ARG").toInt()
        val expectedCallCount =
            env("KDBUS_INTEROP_EXPECT_CALL_COUNT")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val delayMs = env("KDBUS_INTEROP_DELAY_MS")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val failMessage = env("KDBUS_INTEROP_FAIL_MESSAGE")
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val observedArg = atomic(Int.MIN_VALUE)
        val callCount = atomic(0)

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(methodName)) {
                    inputParamNames = listOf("value")
                    outputParamNames = listOf("value")
                    call { value: Int ->
                        observedArg.value = value
                        callCount.incrementAndGet()
                        if (delayMs > 0) usleep((delayMs * 1_000L).convert())
                        failMessage?.let { message -> throw createError(1, message) }
                        value + 1
                    }
                }
            }

            try {
                assertTrue(
                    waitUntil({ callCount.value >= expectedCallCount }, timeout = 15.seconds),
                    "Timed out waiting for JVM client invocation"
                )
                if (expectedCallCount == 1) {
                    assertEquals(expectedArg, observedArg.value)
                }
            } finally {
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun jvmClientCanObserveSignalFromNativePeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server-signal") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val methodName = requireEnv("KDBUS_INTEROP_METHOD")
        val signalName = requireEnv("KDBUS_INTEROP_SIGNAL")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val signalEmitted = atomic(false)

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(methodName)) {
                    inputParamNames = listOf("value")
                    outputParamNames = listOf("value")
                    call { value: Int ->
                        obj.emitSignal(InterfaceName(interfaceName), SignalName(signalName)) {
                            call(value)
                        }
                        signalEmitted.value = true
                        value + 1
                    }
                }
            }

            try {
                assertTrue(
                    waitUntil(signalEmitted, timeout = 15.seconds),
                    "Timed out waiting for JVM client to trigger native signal"
                )
            } finally {
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun jvmClientCanObservePropertiesChangedFromNativePeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server-properties") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val methodName = requireEnv("KDBUS_INTEROP_METHOD")
        val propertyName = requireEnv("KDBUS_INTEROP_PROPERTY")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedValue = requireEnv("KDBUS_INTEROP_EXPECTED_ARG").toInt()
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val signalEmitted = atomic(false)

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(methodName)) {
                    outputParamNames = listOf("value")
                    call { ->
                        obj.emitPropertiesChangedSignal(
                            InterfaceName(interfaceName),
                            listOf(PropertyName(propertyName))
                        )
                        signalEmitted.value = true
                        1
                    }
                }
                prop(PropertyName(propertyName)) {
                    withGetter { expectedValue }
                }
            }

            try {
                assertTrue(
                    waitUntil(signalEmitted, timeout = 15.seconds),
                    "Timed out waiting for JVM client to trigger native properties signal"
                )
            } finally {
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun jvmClientCanRoundTripUnixFdOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server-fd") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedByte = requireEnv("KDBUS_INTEROP_EXPECTED_ARG").toInt() and 0xFF
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val verificationComplete = atomic(false)
        val invalidRejected = atomic(false)
        var storedReadFd: UnixFd? = null
        var storedWriteFd = -1

        fun clearStoredPipe() {
            storedReadFd?.release()
            storedReadFd = null
            if (storedWriteFd >= 0) {
                close(storedWriteFd)
                storedWriteFd = -1
            }
        }

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(CREATE_PIPE_READ_FD_METHOD)) {
                    outputParamNames = listOf("fd")
                    call { ->
                        clearStoredPipe()
                        val (readFd, writeFd) = createPipePair()
                        storedWriteFd = writeFd
                        UnixFd(readFd, adoptFd = Unit)
                    }
                }
                method(MethodName(STORE_PIPE_READ_FD_METHOD)) {
                    inputParamNames = listOf("fd")
                    outputParamNames = listOf("accepted")
                    call { fd: UnixFd ->
                        if (fd.fd < 0) {
                            invalidRejected.value = true
                            throw createError(1, "Invalid Unix FD")
                        }
                        storedReadFd?.release()
                        storedReadFd = fd
                        1
                    }
                }
                method(MethodName(VERIFY_STORED_PIPE_READ_FD_METHOD)) {
                    inputParamNames = listOf("expectedByte")
                    outputParamNames = listOf("observedByte")
                    call { expected: Int ->
                        val readFd = storedReadFd ?: throw createError(1, "No stored Unix FD")
                        val writeFd = storedWriteFd
                        if (writeFd < 0) throw createError(1, "No stored writer FD")
                        if ((expected and 0xFF) != expectedByte) {
                            throw createError(1, "Unexpected verify byte value $expected")
                        }
                        val observed = pipeByteRoundTrip(writeFd, readFd.fd, expected)
                        val senderFd = readFd.fd
                        readFd.release()
                        storedReadFd = null
                        close(writeFd)
                        storedWriteFd = -1
                        if (close(senderFd) == 0) {
                            throw createError(1, "Receiver release did not close FD exactly once")
                        }
                        if (observed != (expected and 0xFF)) {
                            throw createError(1, "Pipe payload mismatch")
                        }
                        verificationComplete.value = true
                        observed
                    }
                }
            }

            try {
                assertTrue(
                    waitUntil(
                        { verificationComplete.value && invalidRejected.value },
                        timeout = 20.seconds
                    ),
                    "Timed out waiting for JVM client FD verification + invalid rejection"
                )
                assertEquals(true, verificationComplete.value)
                assertEquals(true, invalidRejected.value)
            } finally {
                clearStoredPipe()
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun jvmClientCanRoundTripLargeMapOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server-large-map") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedSize = requireEnv("KDBUS_INTEROP_EXPECTED_SIZE").toInt()
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val receivedValidPayload = atomic(false)

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(ROUND_TRIP_LARGE_MAP_METHOD)) {
                    inputParamNames = listOf("payload")
                    outputParamNames = listOf("payload")
                    call { payload: Map<Int, String> ->
                        if (payload.size != expectedSize) {
                            throw createError(
                                1,
                                "Expected map size=$expectedSize but was ${payload.size}"
                            )
                        }
                        if (!payload.all { (key, value) -> value == "value-$key" }) {
                            throw createError(1, "Large map payload content mismatch")
                        }
                        receivedValidPayload.value = true
                        payload
                    }
                }
            }

            try {
                assertTrue(
                    waitUntil(receivedValidPayload, timeout = 20.seconds),
                    "Timed out waiting for JVM client large-map round trip"
                )
            } finally {
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun jvmClientCanRoundTripNestedVariantOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server-nested-variant") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedSize = requireEnv("KDBUS_INTEROP_EXPECTED_SIZE").toInt()
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val receivedValidPayload = atomic(false)

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(ROUND_TRIP_NESTED_VARIANT_METHOD)) {
                    inputParamNames = listOf("payload")
                    outputParamNames = listOf("payload")
                    call { payload: Variant ->
                        val decoded = payload.get<Map<String, List<Int>>>()
                        if (!isValidNestedPayload(decoded, expectedSize)) {
                            throw createError(1, "Nested variant payload content mismatch")
                        }
                        receivedValidPayload.value = true
                        payload
                    }
                }
            }

            try {
                assertTrue(
                    waitUntil(receivedValidPayload, timeout = 20.seconds),
                    "Timed out waiting for JVM client nested-variant round trip"
                )
            } finally {
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun jvmClientCanRoundTripMixedPayloadOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "server-mixed-payload") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedSize = requireEnv("KDBUS_INTEROP_EXPECTED_SIZE").toInt()
        val sock = openUnixSocket(socketPath)
        val acceptContext = newFixedThreadPoolContext(1, "interop-accept")
        val connectionDeferred = CompletableDeferred<com.monkopedia.sdbus.Connection>()
        val receivedValidPayload = atomic(false)

        try {
            val acceptJob = launch(acceptContext) {
                val fd = accept(sock, null, null)
                require(fd >= 0) { "accept() failed for socket $socketPath" }
                val set = fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                require(set >= 0) { "fcntl(F_SETFD) failed for accepted fd=$fd" }
                val connection = createServerBus(fd)
                connection.enterEventLoopAsync()
                connectionDeferred.complete(connection)
            }

            val connection = withTimeout(15_000) { connectionDeferred.await() }
            val obj = createObject(connection, ObjectPath(objectPath))
            val registration = obj.addVTable(InterfaceName(interfaceName)) {
                method(MethodName(ROUND_TRIP_MIXED_PAYLOAD_METHOD)) {
                    inputParamNames = listOf("payload")
                    outputParamNames = listOf("payload")
                    call { payload: Map<String, Variant> ->
                        if (!isValidMixedPayload(payload, expectedSize)) {
                            throw createError(1, "Mixed payload content mismatch")
                        }
                        receivedValidPayload.value = true
                        payload
                    }
                }
            }

            try {
                assertTrue(
                    waitUntil(receivedValidPayload, timeout = 20.seconds),
                    "Timed out waiting for JVM client mixed-payload round trip"
                )
            } finally {
                registration.release()
                obj.release()
                connection.leaveEventLoop()
                connection.release()
                acceptJob.cancel()
            }
        } finally {
            acceptContext.close()
            close(sock)
            unlink(socketPath)
        }
    }

    @Test
    fun nativeClientCanInvokeIncrementOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val methodName = requireEnv("KDBUS_INTEROP_METHOD")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedArg = requireEnv("KDBUS_INTEROP_EXPECTED_ARG").toInt()
        val timeoutMs = env("KDBUS_INTEROP_TIMEOUT_MS")?.toLongOrNull()?.coerceAtLeast(1L) ?: 5_000L
        val expectError = env("KDBUS_INTEROP_EXPECT_ERROR") == "true"
        val expectedErrorContains = env("KDBUS_INTEROP_EXPECT_ERROR_CONTAINS")
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        try {
            fun invoke(): Int = proxy.callMethod<Int>(
                InterfaceName(interfaceName),
                MethodName(methodName)
            ) {
                timeout = timeoutMs.milliseconds
                call(expectedArg)
            }

            if (expectError) {
                val failure = waitForFailure(timeoutMillis = 15_000) {
                    invoke()
                }
                expectedErrorContains?.let { expected ->
                    val message = failure.message ?: failure.toString()
                    assertTrue(
                        message.contains(expected, ignoreCase = true),
                        "Expected error containing '$expected' but was '$message'"
                    )
                }
                return@runBlocking
            }

            val result = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Int>(
                    InterfaceName(interfaceName),
                    MethodName(methodName)
                ) {
                    timeout = timeoutMs.milliseconds
                    call(expectedArg)
                }
            }
            assertEquals(expectedArg + 1, result)
        } finally {
            proxy.release()
            connection.release()
        }
    }

    @Test
    fun nativeClientCanObserveSignalFromJvmPeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client-signal") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val methodName = requireEnv("KDBUS_INTEROP_METHOD")
        val signalName = requireEnv("KDBUS_INTEROP_SIGNAL")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedArg = requireEnv("KDBUS_INTEROP_EXPECTED_ARG").toInt()
        val observedSignalValue = atomic(Int.MIN_VALUE)
        val signalReceived = atomic(false)
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        connection.enterEventLoopAsync()
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        val signalRegistration = proxy.onSignal(
            InterfaceName(interfaceName),
            SignalName(signalName)
        ) {
            call { value: Int ->
                observedSignalValue.value = value
                signalReceived.value = true
            }
        }
        try {
            val result = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Int>(
                    InterfaceName(interfaceName),
                    MethodName(methodName)
                ) {
                    timeout = 5.seconds
                    call(expectedArg)
                }
            }
            assertEquals(expectedArg + 1, result)
            assertTrue(
                waitUntil(signalReceived, timeout = 15.seconds),
                "Timed out waiting for signal from JVM peer"
            )
            assertEquals(expectedArg, observedSignalValue.value)
        } finally {
            signalRegistration.release()
            proxy.release()
            connection.leaveEventLoop()
            connection.release()
        }
    }

    @Test
    fun nativeClientCanObservePropertiesChangedFromJvmPeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client-properties") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val methodName = requireEnv("KDBUS_INTEROP_METHOD")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val signalReceived = atomic(false)
        val signalMember = atomic<String?>(null)
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        connection.enterEventLoopAsync()
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        val signalRegistration = proxy.registerSignalHandler(
            InterfaceName("org.freedesktop.DBus.Properties"),
            SignalName("PropertiesChanged")
        ) { message ->
            signalMember.value = message.getMemberName()
            signalReceived.value = true
        }
        try {
            val result = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Int>(
                    InterfaceName(interfaceName),
                    MethodName(methodName)
                ) {
                    timeout = 5.seconds
                    call()
                }
            }
            assertEquals(1, result)
            assertTrue(
                waitUntil(signalReceived, timeout = 15.seconds),
                "Timed out waiting for properties signal from JVM peer"
            )
            assertEquals("PropertiesChanged", signalMember.value)
        } finally {
            signalRegistration.release()
            proxy.release()
            connection.leaveEventLoop()
            connection.release()
        }
    }

    @Test
    fun nativeClientCanRoundTripUnixFdWithJvmPeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client-fd") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedByte = requireEnv("KDBUS_INTEROP_EXPECTED_ARG").toInt() and 0xFF
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        val (readFd, writeFd) = createPipePair()
        val outboundFd = UnixFd(readFd, adoptFd = Unit)
        try {
            val accepted = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Int>(
                    InterfaceName(interfaceName),
                    MethodName(STORE_PIPE_READ_FD_METHOD)
                ) {
                    timeout = 5.seconds
                    call(outboundFd)
                }
            }
            assertEquals(1, accepted)
            val senderFd = outboundFd.fd
            outboundFd.release()
            assertEquals(-1, close(senderFd))

            val roundTrippedFd = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<UnixFd>(
                    InterfaceName(interfaceName),
                    MethodName(TAKE_STORED_PIPE_READ_FD_METHOD)
                ) {
                    timeout = 5.seconds
                    call()
                }
            }
            try {
                val observed = pipeByteRoundTrip(writeFd, roundTrippedFd.fd, expectedByte)
                assertEquals(expectedByte, observed)
                val receivedFd = roundTrippedFd.fd
                roundTrippedFd.release()
                assertEquals(-1, close(receivedFd))
            } finally {
                roundTrippedFd.release()
            }

            val invalidError = runCatching {
                proxy.callMethod<Int>(
                    InterfaceName(interfaceName),
                    MethodName(STORE_PIPE_READ_FD_METHOD)
                ) {
                    timeout = 5.seconds
                    call(UnixFd(-1, adoptFd = Unit))
                }
            }.exceptionOrNull() as? com.monkopedia.sdbus.Error
            assertTrue(invalidError != null, "Expected invalid Unix FD call to fail")
            assertTrue(
                invalidError.errorMessage.contains("Invalid Unix FD", ignoreCase = true),
                "Unexpected invalid Unix FD error message: ${invalidError.errorMessage}"
            )
        } finally {
            close(writeFd)
            outboundFd.release()
            proxy.release()
            connection.release()
        }
    }

    @Test
    fun nativeClientCanRoundTripLargeMapWithJvmPeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client-large-map") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedSize = requireEnv("KDBUS_INTEROP_EXPECTED_SIZE").toInt()
        val payload = buildLargeMapPayload(expectedSize)
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        try {
            val result = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Map<Int, String>>(
                    InterfaceName(interfaceName),
                    MethodName(ROUND_TRIP_LARGE_MAP_METHOD)
                ) {
                    timeout = 5.seconds
                    call(payload)
                }
            }
            assertEquals(payload, result)
        } finally {
            proxy.release()
            connection.release()
        }
    }

    @Test
    fun nativeClientCanRoundTripNestedVariantWithJvmPeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client-nested-variant") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedSize = requireEnv("KDBUS_INTEROP_EXPECTED_SIZE").toInt()
        val payload = buildNestedVariantPayload(expectedSize)
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        try {
            val result = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Variant>(
                    InterfaceName(interfaceName),
                    MethodName(ROUND_TRIP_NESTED_VARIANT_METHOD)
                ) {
                    timeout = 5.seconds
                    call(payload)
                }
            }
            assertEquals(
                payload.get<Map<String, List<Int>>>(),
                result.get<Map<String, List<Int>>>()
            )
        } finally {
            proxy.release()
            connection.release()
        }
    }

    @Test
    fun nativeClientCanRoundTripMixedPayloadWithJvmPeerOverDirectBus() = runBlocking {
        if (env("KDBUS_NATIVE_INTEROP_ROLE") != "client-mixed-payload") return@runBlocking

        val socketPath = requireEnv("KDBUS_INTEROP_SOCKET")
        val connectionAddress = env("KDBUS_INTEROP_ADDRESS") ?: "unix:path=$socketPath"
        val interfaceName = requireEnv("KDBUS_INTEROP_INTERFACE")
        val objectPath = requireEnv("KDBUS_INTEROP_OBJECT_PATH")
        val expectedSize = requireEnv("KDBUS_INTEROP_EXPECTED_SIZE").toInt()
        val payload = buildMixedPayload(expectedSize)
        val connection = if (env("KDBUS_INTEROP_CONNECT_FD") == "true") {
            val fd = connectUnixSocket(socketPath)
            createDirectBusConnection(fd)
        } else {
            createDirectBusConnection(connectionAddress)
        }
        val proxy = createProxy(
            connection,
            ServiceName(""),
            ObjectPath(objectPath),
            true
        )
        try {
            val result = retryCall(timeoutMillis = 15_000) {
                proxy.callMethod<Map<String, Variant>>(
                    InterfaceName(interfaceName),
                    MethodName(ROUND_TRIP_MIXED_PAYLOAD_METHOD)
                ) {
                    timeout = 5.seconds
                    call(payload)
                }
            }
            assertTrue(isValidMixedPayload(result, expectedSize))
        } finally {
            proxy.release()
            connection.release()
        }
    }

    private fun requireEnv(name: String): String = env(name)
        ?: error("Missing required environment variable '$name'")

    private fun env(name: String): String? = getenv(name)?.toKString()

    private suspend fun <T> retryCall(timeoutMillis: Long, call: () -> T): T {
        val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
        var lastError: Throwable? = null
        while (startedAt.elapsedNow().inWholeMilliseconds < timeoutMillis) {
            val result = runCatching(call)
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
            delay(50)
        }
        throw AssertionError("Timed out waiting for call to succeed", lastError)
    }

    private suspend fun <T> waitForFailure(timeoutMillis: Long, call: () -> T): Throwable {
        val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
        while (startedAt.elapsedNow().inWholeMilliseconds < timeoutMillis) {
            val result = runCatching(call)
            if (result.isFailure) {
                return result.exceptionOrNull() ?: AssertionError("Call failed without throwable")
            }
            delay(50)
        }
        throw AssertionError("Timed out waiting for call failure")
    }

    private fun openUnixSocket(path: String): Int = memScoped {
        unlink(path)
        val sock = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
        require(sock >= 0) { "Create socket failed for $path" }
        val sa = cValue<sockaddr_un>().getPointer(this)
        memset(sa, 0, sizeOf<sockaddr_un>().convert())
        sa[0].sun_family = AF_UNIX.convert()
        val size = sizeOf<sockaddr_un>() - sizeOf<sa_family_tVar>()
        snprintf(sa[0].sun_path, size.convert(), "%s", path.cstr)
        umask(0u)
        val bindResult = bind(sock, sa.reinterpret(), sizeOf<sockaddr_un>().convert())
        require(bindResult >= 0) { "Bind failed for $path: $bindResult" }
        val listenResult = listen(sock, 5)
        require(listenResult >= 0) { "Listen failed for $path: $listenResult" }
        return sock
    }

    private fun connectUnixSocket(path: String): Int = memScoped {
        val sock = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
        require(sock >= 0) { "Create client socket failed for $path" }
        val sa = cValue<sockaddr_un>().getPointer(this)
        memset(sa, 0, sizeOf<sockaddr_un>().convert())
        sa[0].sun_family = AF_UNIX.convert()
        val size = sizeOf<sockaddr_un>() - sizeOf<sa_family_tVar>()
        snprintf(sa[0].sun_path, size.convert(), "%s", path.cstr)
        val connectResult = connect(sock, sa.reinterpret(), sizeOf<sockaddr_un>().convert())
        require(connectResult >= 0) { "Connect failed for $path: $connectResult" }
        return sock
    }

    private fun createPipePair(): Pair<Int, Int> = memScoped {
        val pair = allocArray<IntVar>(2)
        val result = pipe(pair)
        require(result == 0) { "pipe() failed with code $result" }
        pair[0] to pair[1]
    }

    private fun pipeByteRoundTrip(writeFd: Int, readFd: Int, expectedByte: Int): Int = memScoped {
        val expected = (expectedByte and 0xFF).toByte()
        val writeBuffer = byteArrayOf(expected)
        val written = writeBuffer.usePinned { pinned ->
            write(writeFd, pinned.addressOf(0), 1.convert())
        }
        require(written == 1L) { "write() failed for fd=$writeFd with result=$written" }

        val readBuffer = ByteArray(1)
        val readCount = readBuffer.usePinned { pinned ->
            read(readFd, pinned.addressOf(0), 1.convert())
        }
        require(readCount == 1L) { "read() failed for fd=$readFd with result=$readCount" }
        readBuffer[0].toInt() and 0xFF
    }

    private fun buildLargeMapPayload(size: Int): Map<Int, String> = buildMap {
        repeat(size) { index ->
            this[index] = "value-$index"
        }
    }

    private fun buildNestedVariantPayload(size: Int): Variant = Variant(
        buildMap<String, List<Int>> {
            repeat(size) { index ->
                this["key-$index"] = listOf(index, index + 1, index * 2)
            }
        }
    )

    private fun buildMixedPayload(size: Int): Map<String, Variant> = mapOf(
        "count" to Variant(size),
        "labels" to Variant(listOf("alpha", "beta", "gamma")),
        "scalars" to Variant(mapOf("left" to size, "right" to size * 2)),
        "nested" to buildNestedVariantPayload(size),
        "optionalPresent" to Variant("value-$size")
    )

    private fun isValidNestedPayload(payload: Map<String, List<Int>>, expectedSize: Int): Boolean {
        if (payload.size != expectedSize) return false
        return payload.all { (key, values) ->
            val index = key.removePrefix("key-").toIntOrNull() ?: return@all false
            values == listOf(index, index + 1, index * 2)
        }
    }

    private fun isValidMixedPayload(payload: Map<String, Variant>, expectedSize: Int): Boolean {
        val count = runCatching { payload["count"]?.get<Int>() }.getOrNull() ?: return false
        if (count != expectedSize) return false

        val labels = runCatching { payload["labels"]?.get<List<String>>() }.getOrNull()
            ?: return false
        if (labels != listOf("alpha", "beta", "gamma")) return false

        val scalars = runCatching { payload["scalars"]?.get<Map<String, Int>>() }.getOrNull()
            ?: return false
        if (scalars != mapOf("left" to expectedSize, "right" to expectedSize * 2)) return false

        val nested = runCatching { payload["nested"]?.get<Map<String, List<Int>>>() }.getOrNull()
            ?: return false
        if (!isValidNestedPayload(nested, expectedSize)) return false

        val optionalPresent = runCatching { payload["optionalPresent"]?.get<String>() }
            .getOrNull() ?: return false
        if (optionalPresent != "value-$expectedSize") return false

        return "optionalAbsent" !in payload
    }

    private companion object {
        private const val CREATE_PIPE_READ_FD_METHOD = "CreatePipeReadFd"
        private const val STORE_PIPE_READ_FD_METHOD = "StorePipeReadFd"
        private const val VERIFY_STORED_PIPE_READ_FD_METHOD = "VerifyStoredPipeReadFd"
        private const val TAKE_STORED_PIPE_READ_FD_METHOD = "TakeStoredPipeReadFd"
        private const val ROUND_TRIP_LARGE_MAP_METHOD = "RoundTripLargeMap"
        private const val ROUND_TRIP_NESTED_VARIANT_METHOD = "RoundTripNestedVariant"
        private const val ROUND_TRIP_MIXED_PAYLOAD_METHOD = "RoundTripMixedPayload"
    }
}
