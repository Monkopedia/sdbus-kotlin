package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createDirectBusConnection
import com.monkopedia.sdbus.createProxy
import java.io.ByteArrayOutputStream
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import java.text.CollationKey
import java.text.Collator
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DirectConnectionBuilder
import org.freedesktop.dbus.connections.transports.TransportBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal

class CrossRuntimeInteropSmokeTest {
    @Test
    fun jvmClientInvokesNativePeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = "org.monkopedia.sdbus.phase3"
        val methodName = "Increment"
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedArg = 33
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanInvokeIncrementOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_METHOD"] = methodName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedArg.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val connection = createDirectBusConnection("unix:path=$socketPath")
            val proxy = createProxy(
                connection,
                ServiceName(""),
                ObjectPath(objectPath),
                dontRunEventLoopThread = true
            )
            try {
                val result = retryCall(timeoutMillis = 10_000) {
                    proxy.callMethod<Int>(
                        InterfaceName(interfaceName),
                        MethodName(methodName)
                    ) {
                        call(expectedArg)
                    }
                }
                assertEquals(expectedArg + 1, result)
            } finally {
                proxy.release()
                connection.release()
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun jvmClientObservesNativeSignalOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-signal-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = "org.monkopedia.sdbus.phase3"
        val methodName = "EmitSignal"
        val signalName = "Changed"
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedArg = 45
        val seenValue = AtomicInteger(Int.MIN_VALUE)
        val signalLatch = CountDownLatch(1)
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanObserveSignalFromNativePeerOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server-signal"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_METHOD"] = methodName
            builder.environment()["KDBUS_INTEROP_SIGNAL"] = signalName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedArg.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val javaConnection = DirectConnectionBuilder.forAddress("unix:path=$socketPath").build()
            val peer = javaConnection.getRemoteObject(objectPath, Phase3SignalPeer::class.java)
            val signalRegistration = javaConnection.addSigHandler(
                Phase3SignalPeer.Changed::class.java
            ) { signal ->
                if (signal.path == objectPath && signal.value == expectedArg) {
                    seenValue.set(signal.value)
                    signalLatch.countDown()
                }
            }
            try {
                val result = retryCall(timeoutMillis = 10_000) { peer.emitSignal(expectedArg) }
                assertEquals(expectedArg + 1, result)
                assertTrue(
                    signalLatch.await(5, TimeUnit.SECONDS),
                    "Signal not received from native peer"
                )
                assertEquals(expectedArg, seenValue.get())
            } finally {
                signalRegistration.close()
                runCatching { javaConnection.disconnect() }
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun jvmClientObservesNativePropertiesChangedOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-properties-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = "org.monkopedia.sdbus.phase3"
        val methodName = "EmitPropertiesChanged"
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val propertyName = "state"
        val expectedArg = 51
        val seenMember = AtomicReference<String?>(null)
        val seenInterfaceName = AtomicReference<String?>(null)
        val signalLatch = CountDownLatch(1)
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanObservePropertiesChangedFromNativePeerOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server-properties"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_METHOD"] = methodName
            builder.environment()["KDBUS_INTEROP_PROPERTY"] = propertyName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedArg.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val javaConnection = DirectConnectionBuilder.forAddress("unix:path=$socketPath").build()
            val peer = javaConnection.getRemoteObject(objectPath, Phase3PropertiesPeer::class.java)
            val signalRegistration = javaConnection.addSigHandler(
                PropertiesSignalPeer.PropertiesChanged::class.java
            ) { signal ->
                if (signal.path == objectPath) {
                    seenMember.set("PropertiesChanged")
                    seenInterfaceName.set(signal.interfaceName)
                    signalLatch.countDown()
                }
            }
            try {
                val result = retryCall(timeoutMillis = 10_000) { peer.emitPropertiesChanged() }
                assertEquals(1, result)
                assertTrue(
                    signalLatch.await(5, TimeUnit.SECONDS),
                    "PropertiesChanged signal not received from native peer"
                )
                assertEquals("PropertiesChanged", seenMember.get())
                assertEquals(interfaceName, seenInterfaceName.get())
            } finally {
                signalRegistration.close()
                runCatching { javaConnection.disconnect() }
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun jvmClientRoundTripsUnixFdWithNativePeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-fd-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedByte = 0x4D
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanRoundTripUnixFdOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server-fd"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedByte.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val connection = createDirectBusConnection("unix:path=$socketPath")
            val proxy = createProxy(
                connection,
                ServiceName(""),
                ObjectPath(objectPath),
                dontRunEventLoopThread = true
            )
            val createdFd = retryCall(timeoutMillis = 10_000) {
                proxy.callMethod<UnixFd>(
                    InterfaceName(interfaceName),
                    MethodName(CREATE_PIPE_READ_FD_METHOD)
                ) {
                    call()
                }
            }
            try {
                val accepted = retryCall(timeoutMillis = 10_000) {
                    proxy.callMethod<Int>(
                        InterfaceName(interfaceName),
                        MethodName(STORE_PIPE_READ_FD_METHOD)
                    ) {
                        call(createdFd)
                    }
                }
                assertEquals(1, accepted)
                createdFd.release()

                val observed = retryCall(timeoutMillis = 10_000) {
                    proxy.callMethod<Int>(
                        InterfaceName(interfaceName),
                        MethodName(VERIFY_STORED_PIPE_READ_FD_METHOD)
                    ) {
                        call(expectedByte)
                    }
                }
                assertEquals(expectedByte, observed)

                val invalidFailure = runCatching {
                    proxy.callMethod<Int>(
                        InterfaceName(interfaceName),
                        MethodName(STORE_PIPE_READ_FD_METHOD)
                    ) {
                        call(UnixFd(-1, adoptFd = Unit))
                    }
                }.exceptionOrNull() as? com.monkopedia.sdbus.Error
                assertTrue(invalidFailure != null, "Expected invalid Unix FD call to fail")
                val message = invalidFailure.errorMessage
                assertTrue(
                    message.contains("Invalid Unix FD", ignoreCase = true) ||
                        message.contains("FileDescriptor", ignoreCase = true) ||
                        message.contains("opens java.io", ignoreCase = true) ||
                        message.contains("Underlying transport returned -1", ignoreCase = true),
                    "Unexpected invalid Unix FD failure message: $message"
                )
            } finally {
                createdFd.release()
                proxy.release()
                connection.release()
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun jvmClientRoundTripsLargeMapWithNativePeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-large-map-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedSize = 512
        val payload = buildLargeMapPayload(expectedSize)
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanRoundTripLargeMapOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server-large-map"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_SIZE"] = expectedSize.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val connection = createDirectBusConnection("unix:path=$socketPath")
            val proxy = createProxy(
                connection,
                ServiceName(""),
                ObjectPath(objectPath),
                dontRunEventLoopThread = true
            )
            try {
                val result = retryCall(timeoutMillis = 10_000) {
                    proxy.callMethod<Map<Int, String>>(
                        InterfaceName(interfaceName),
                        MethodName(ROUND_TRIP_LARGE_MAP_METHOD)
                    ) {
                        call(payload)
                    }
                }
                assertEquals(payload, result)
            } finally {
                proxy.release()
                connection.release()
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun jvmClientRoundTripsNestedVariantWithNativePeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-nested-variant-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedSize = 64
        val payload = buildNestedVariantPayload(expectedSize)
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanRoundTripNestedVariantOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server-nested-variant"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_SIZE"] = expectedSize.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val connection = createDirectBusConnection("unix:path=$socketPath")
            val proxy = createProxy(
                connection,
                ServiceName(""),
                ObjectPath(objectPath),
                dontRunEventLoopThread = true
            )
            try {
                val result = retryCall(timeoutMillis = 10_000) {
                    proxy.callMethod<Variant>(
                        InterfaceName(interfaceName),
                        MethodName(ROUND_TRIP_NESTED_VARIANT_METHOD)
                    ) {
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

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun jvmClientRoundTripsMixedPayloadWithNativePeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-jvm-native-mixed-payload-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedSize = 64
        val payload = buildMixedPayload(expectedSize)
        val nativeOutput = ByteArrayOutputStream()
        val process = ProcessBuilder(
            kexe.toString(),
            "--ktest_no_exit_code",
            "--ktest_logger=TEAMCITY",
            "--ktest_gradle_filter=*NativeInteropPeerTest.jvmClientCanRoundTripMixedPayloadOverDirectBus*"
        ).also { builder ->
            builder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "server-mixed-payload"
            builder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
            builder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
            builder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
            builder.environment()["KDBUS_INTEROP_EXPECTED_SIZE"] = expectedSize.toString()
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )

            val connection = createDirectBusConnection("unix:path=$socketPath")
            val proxy = createProxy(
                connection,
                ServiceName(""),
                ObjectPath(objectPath),
                dontRunEventLoopThread = true
            )
            try {
                val result = retryCall(timeoutMillis = 10_000) {
                    proxy.callMethod<Map<String, Variant>>(
                        InterfaceName(interfaceName),
                        MethodName(ROUND_TRIP_MIXED_PAYLOAD_METHOD)
                    ) {
                        call(payload)
                    }
                }
                assertTrue(isValidMixedPayload(result, expectedSize))
            } finally {
                proxy.release()
                connection.release()
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(
                0,
                process.exitValue(),
                "Native peer failed. Output:\n$nativeLog"
            )
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientInvokesJvmPeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = "org.monkopedia.sdbus.phase3"
        val methodName = "Increment"
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedArg = 57
        val observedArg = AtomicInteger(Int.MIN_VALUE)
        val invoked = CountDownLatch(1)
        val guid = UUID.randomUUID().toString().replace("-", "")
        // Workaround for dbus-java SASL NPE with sd-bus peers:
        // https://github.com/hypfvieh/dbus-java/issues/294
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3ServerObject(objectPath, observedArg, invoked)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanInvokeIncrementOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_METHOD"] = methodName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedArg.toString()
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertTrue(
                invoked.await(5, TimeUnit.SECONDS),
                "JVM server did not observe native invocation for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertEquals(expectedArg, observedArg.get())
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientObservesJvmSignalOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-signal-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = "org.monkopedia.sdbus.phase3"
        val methodName = "EmitSignal"
        val signalName = "Changed"
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedArg = 67
        val guid = UUID.randomUUID().toString().replace("-", "")
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3SignalServerObject(objectPath, connection)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanObserveSignalFromJvmPeerOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client-signal"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_METHOD"] = methodName
                procBuilder.environment()["KDBUS_INTEROP_SIGNAL"] = signalName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedArg.toString()
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientObservesJvmPropertiesChangedOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-properties-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = "org.monkopedia.sdbus.phase3"
        val methodName = "EmitPropertiesChanged"
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val guid = UUID.randomUUID().toString().replace("-", "")
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3PropertiesServerObject(objectPath, connection)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanObservePropertiesChangedFromJvmPeerOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client-properties"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_METHOD"] = methodName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientRoundTripsUnixFdWithJvmPeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-fd-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedByte = 0x71
        val guid = UUID.randomUUID().toString().replace("-", "")
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3FdServerObject(objectPath)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanRoundTripUnixFdWithJvmPeerOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client-fd"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_EXPECTED_ARG"] = expectedByte.toString()
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientRoundTripsLargeMapWithJvmPeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-large-map-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedSize = 512
        val observedSize = AtomicInteger(-1)
        val invoked = CountDownLatch(1)
        val guid = UUID.randomUUID().toString().replace("-", "")
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3LargeMapServerObject(objectPath, expectedSize, observedSize, invoked)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanRoundTripLargeMapWithJvmPeerOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client-large-map"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_EXPECTED_SIZE"] = expectedSize.toString()
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertTrue(
                invoked.await(5, TimeUnit.SECONDS),
                "JVM server did not observe large-map invocation. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertEquals(expectedSize, observedSize.get())
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientRoundTripsNestedVariantWithJvmPeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-nested-variant-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedSize = 64
        val observedSize = AtomicInteger(-1)
        val invoked = CountDownLatch(1)
        val guid = UUID.randomUUID().toString().replace("-", "")
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3NestedVariantServerObject(objectPath, expectedSize, observedSize, invoked)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanRoundTripNestedVariantWithJvmPeerOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client-nested-variant"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_EXPECTED_SIZE"] = expectedSize.toString()
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertTrue(
                invoked.await(5, TimeUnit.SECONDS),
                "JVM server did not observe nested-variant invocation. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertEquals(expectedSize, observedSize.get())
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    @Test
    fun nativeClientRoundTripsMixedPayloadWithJvmPeerOverDirectBus() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-cross-native-jvm-mixed-payload-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val objectPath = "/org/monkopedia/sdbus/phase3/Object"
        val expectedSize = 64
        val observedSize = AtomicInteger(-1)
        val invoked = CountDownLatch(1)
        val guid = UUID.randomUUID().toString().replace("-", "")
        val restoreSaslCollator = installNullSafeSaslCollatorWorkaround()

        val builder = DirectConnectionBuilder
            .forAddress("unix:path=$socketPath,guid=$guid,listen=true")
        builder.transportConfig()
            .configureSasl()
            .withAuthMode(TransportBuilder.SaslAuthMode.AUTH_ANONYMOUS)
            .back()
            .back()
        val connection = builder.build()
        val connectionAddress = connection.address.toString().replace(",listen=true", "")
        connection.exportObject(
            objectPath,
            Phase3MixedPayloadServerObject(objectPath, expectedSize, observedSize, invoked)
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(start = true, isDaemon = true, name = "cross-jvm-direct-listen") {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis = 10_000),
                "Socket not ready: $socketPath"
            )
            val launchedProcess = ProcessBuilder(
                kexe.toString(),
                "--ktest_no_exit_code",
                "--ktest_logger=TEAMCITY",
                "--ktest_gradle_filter=*NativeInteropPeerTest.nativeClientCanRoundTripMixedPayloadWithJvmPeerOverDirectBus*"
            ).also { procBuilder ->
                procBuilder.environment()["KDBUS_NATIVE_INTEROP_ROLE"] = "client-mixed-payload"
                procBuilder.environment()["KDBUS_INTEROP_SOCKET"] = socketPath.toString()
                procBuilder.environment()["KDBUS_INTEROP_INTERFACE"] = interfaceName
                procBuilder.environment()["KDBUS_INTEROP_OBJECT_PATH"] = objectPath
                procBuilder.environment()["KDBUS_INTEROP_EXPECTED_SIZE"] = expectedSize.toString()
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "cross-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }

            assertTrue(
                launchedProcess.waitFor(30, TimeUnit.SECONDS),
                "Native client did not exit in time for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n" +
                    nativeOutput.toString(Charsets.UTF_8)
            )
            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertEquals(
                0,
                launchedProcess.exitValue(),
                "Native client failed for address '$connectionAddress'. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertTrue(
                invoked.await(5, TimeUnit.SECONDS),
                "JVM server did not observe mixed-payload invocation. " +
                    "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
            )
            assertEquals(expectedSize, observedSize.get())
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    private fun nativeTestBinaryPath(): Path {
        val configured = System.getProperty("kdbus.nativeTestBinary")?.let(Path::of)
        val cwd = Path.of(System.getProperty("user.dir"))
        val candidates = buildList {
            if (configured != null) add(configured)
            add(cwd.resolve("build/bin/linuxX64/debugTest/test.kexe"))
            add(cwd.resolve("../build/bin/linuxX64/debugTest/test.kexe").normalize())
            add(cwd.resolve("../../build/bin/linuxX64/debugTest/test.kexe").normalize())
        }
        return candidates.firstOrNull { Files.isExecutable(it) } ?: candidates.first()
    }

    private fun waitForSocket(path: Path, timeoutMillis: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (Files.exists(path)) return true
            Thread.sleep(25)
        }
        return false
    }

    private fun <T> retryCall(timeoutMillis: Long, call: () -> T): T {
        val start = System.currentTimeMillis()
        var lastError: Throwable? = null
        while (System.currentTimeMillis() - start < timeoutMillis) {
            val result = runCatching(call)
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for native peer call to succeed", lastError)
    }

    private fun installNullSafeSaslCollatorWorkaround(): () -> Unit {
        // Temporary reflection workaround for dbus-java SASL null handling with sd-bus peers.
        // Upstream tracker: https://github.com/hypfvieh/dbus-java/issues/294
        val saslClass = Class.forName("org.freedesktop.dbus.connections.SASL")
        val field = saslClass.getDeclaredField("COL").apply { isAccessible = true }
        val original = field.get(null) as Collator
        val nullSafe = object : Collator() {
            override fun compare(source: String?, target: String?): Int {
                // dbus-java should handle optional SASL fields without throwing;
                // accept nulls as equivalent in this test workaround.
                if (source == null || target == null) return 0
                return original.compare(source, target)
            }

            override fun getCollationKey(source: String?): CollationKey? =
                source?.let(original::getCollationKey)

            override fun hashCode(): Int = original.hashCode()
        }
        setStaticField(field, nullSafe)
        return { setStaticField(field, original) }
    }

    private fun setStaticField(field: Field, value: Any) {
        runCatching {
            field.set(null, value)
        }.onSuccess {
            return
        }

        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        val base = unsafeClass
            .getMethod("staticFieldBase", Field::class.java)
            .invoke(unsafe, field)
        val offset = unsafeClass
            .getMethod("staticFieldOffset", Field::class.java)
            .invoke(unsafe, field) as Long
        unsafeClass
            .getMethod(
                "putObjectVolatile",
                Any::class.java,
                Long::class.javaPrimitiveType,
                Any::class.java
            )
            .invoke(unsafe, base, offset, value)
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3ServerInterface : DBusInterface {
        @DBusMemberName("Increment")
        fun increment(value: Int): Int
    }

    class Phase3ServerObject(
        private val path: String,
        private val observedArg: AtomicInteger,
        private val invoked: CountDownLatch
    ) : Phase3ServerInterface {
        override fun increment(value: Int): Int {
            observedArg.set(value)
            invoked.countDown()
            return value + 1
        }

        override fun getObjectPath(): String = path
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3SignalPeer : DBusInterface {
        @DBusMemberName("EmitSignal")
        fun emitSignal(value: Int): Int

        @DBusMemberName("Changed")
        class Changed(path: String, val value: Int) : DBusSignal(path, value)
    }

    class Phase3SignalServerObject(
        private val path: String,
        private val connection: org.freedesktop.dbus.connections.AbstractConnection
    ) : Phase3SignalPeer {
        override fun emitSignal(value: Int): Int {
            connection.sendMessage(Phase3SignalPeer.Changed(path, value))
            return value + 1
        }

        override fun getObjectPath(): String = path
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3PropertiesPeer : DBusInterface {
        @DBusMemberName("EmitPropertiesChanged")
        fun emitPropertiesChanged(): Int
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3FdPeer : DBusInterface {
        @DBusMemberName("StorePipeReadFd")
        fun storePipeReadFd(fd: org.freedesktop.dbus.FileDescriptor): Int

        @DBusMemberName("TakeStoredPipeReadFd")
        fun takeStoredPipeReadFd(): org.freedesktop.dbus.FileDescriptor
    }

    class Phase3FdServerObject(private val path: String) : Phase3FdPeer {
        private val storedReadFd = AtomicReference<org.freedesktop.dbus.FileDescriptor?>(null)

        override fun storePipeReadFd(fd: org.freedesktop.dbus.FileDescriptor): Int {
            if (fd.intFileDescriptor < 0) {
                throw DBusExecutionException("Invalid Unix FD")
            }
            storedReadFd.set(fd)
            return 1
        }

        override fun takeStoredPipeReadFd(): org.freedesktop.dbus.FileDescriptor =
            storedReadFd.getAndSet(null)
                ?: throw DBusExecutionException("No stored Unix FD")

        override fun getObjectPath(): String = path
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3LargeMapPeer : DBusInterface {
        @DBusMemberName("RoundTripLargeMap")
        fun roundTripLargeMap(payload: Map<Int, String>): Map<Int, String>
    }

    class Phase3LargeMapServerObject(
        private val path: String,
        private val expectedSize: Int,
        private val observedSize: AtomicInteger,
        private val invoked: CountDownLatch
    ) : Phase3LargeMapPeer {
        override fun roundTripLargeMap(payload: Map<Int, String>): Map<Int, String> {
            if (payload.size != expectedSize) {
                throw DBusExecutionException(
                    "Expected map size=$expectedSize but was ${payload.size}"
                )
            }
            if (!payload.all { (key, value) -> value == "value-$key" }) {
                throw DBusExecutionException("Large map payload content mismatch")
            }
            observedSize.set(payload.size)
            invoked.countDown()
            return payload
        }

        override fun getObjectPath(): String = path
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3NestedVariantPeer : DBusInterface {
        @DBusMemberName("RoundTripNestedVariant")
        fun roundTripNestedVariant(
            payload: org.freedesktop.dbus.types.Variant<Any?>
        ): org.freedesktop.dbus.types.Variant<Any?>
    }

    class Phase3NestedVariantServerObject(
        private val path: String,
        private val expectedSize: Int,
        private val observedSize: AtomicInteger,
        private val invoked: CountDownLatch
    ) : Phase3NestedVariantPeer {
        override fun roundTripNestedVariant(
            payload: org.freedesktop.dbus.types.Variant<Any?>
        ): org.freedesktop.dbus.types.Variant<Any?> {
            val decoded = decodeNestedPayload(payload.value)
                ?: throw DBusExecutionException("Nested variant payload decoding failed")
            if (!isValidNestedPayload(decoded, expectedSize)) {
                throw DBusExecutionException("Nested variant payload content mismatch")
            }
            observedSize.set(decoded.size)
            invoked.countDown()
            return payload
        }

        override fun getObjectPath(): String = path

        private fun decodeNestedPayload(value: Any?): Map<String, List<Int>>? {
            val map = value as? Map<*, *> ?: return null
            val decoded = mutableMapOf<String, List<Int>>()
            for ((rawKey, rawValue) in map) {
                val key = rawKey as? String ?: return null
                val list = rawValue as? List<*> ?: return null
                val ints = mutableListOf<Int>()
                for (entry in list) {
                    ints += (entry as? Number)?.toInt() ?: return null
                }
                decoded[key] = ints
            }
            return decoded
        }

        private fun isValidNestedPayload(
            payload: Map<String, List<Int>>,
            expectedSize: Int
        ): Boolean {
            if (payload.size != expectedSize) return false
            return payload.all { (key, values) ->
                val index = key.removePrefix("key-").toIntOrNull() ?: return@all false
                values == listOf(index, index + 1, index * 2)
            }
        }
    }

    @DBusInterfaceName("org.monkopedia.sdbus.phase3")
    interface Phase3MixedPayloadPeer : DBusInterface {
        @DBusMemberName("RoundTripMixedPayload")
        fun roundTripMixedPayload(
            payload: Map<String, org.freedesktop.dbus.types.Variant<Any?>>
        ): Map<String, org.freedesktop.dbus.types.Variant<Any?>>
    }

    class Phase3MixedPayloadServerObject(
        private val path: String,
        private val expectedSize: Int,
        private val observedSize: AtomicInteger,
        private val invoked: CountDownLatch
    ) : Phase3MixedPayloadPeer {
        override fun roundTripMixedPayload(
            payload: Map<String, org.freedesktop.dbus.types.Variant<Any?>>
        ): Map<String, org.freedesktop.dbus.types.Variant<Any?>> {
            if (!isValidMixedPayload(payload, expectedSize)) {
                throw DBusExecutionException("Mixed payload content mismatch")
            }
            observedSize.set(expectedSize)
            invoked.countDown()
            return payload
        }

        override fun getObjectPath(): String = path

        private fun isValidMixedPayload(
            payload: Map<String, org.freedesktop.dbus.types.Variant<Any?>>,
            expectedSize: Int
        ): Boolean {
            val count = (payload["count"]?.value as? Number)?.toInt() ?: return false
            if (count != expectedSize) return false

            val labels = payload["labels"]?.value as? List<*> ?: return false
            if (labels != listOf("alpha", "beta", "gamma")) return false

            val scalars = payload["scalars"]?.value as? Map<*, *> ?: return false
            if (scalars["left"] != expectedSize || scalars["right"] != expectedSize * 2) {
                return false
            }

            val nested = decodeNestedPayload(payload["nested"]?.value) ?: return false
            if (!isValidNestedPayload(nested, expectedSize)) return false

            val optionalPresent = payload["optionalPresent"]?.value as? String ?: return false
            if (optionalPresent != "value-$expectedSize") return false

            return "optionalAbsent" !in payload
        }

        private fun decodeNestedPayload(value: Any?): Map<String, List<Int>>? {
            val map = value as? Map<*, *> ?: return null
            val decoded = mutableMapOf<String, List<Int>>()
            for ((rawKey, rawValue) in map) {
                val key = rawKey as? String ?: return null
                val list = rawValue as? List<*> ?: return null
                val ints = mutableListOf<Int>()
                for (entry in list) {
                    ints += (entry as? Number)?.toInt() ?: return null
                }
                decoded[key] = ints
            }
            return decoded
        }

        private fun isValidNestedPayload(
            payload: Map<String, List<Int>>,
            expectedSize: Int
        ): Boolean {
            if (payload.size != expectedSize) return false
            return payload.all { (key, values) ->
                val index = key.removePrefix("key-").toIntOrNull() ?: return@all false
                values == listOf(index, index + 1, index * 2)
            }
        }
    }

    class Phase3PropertiesServerObject(
        private val path: String,
        private val connection: org.freedesktop.dbus.connections.AbstractConnection
    ) : Phase3PropertiesPeer {
        override fun emitPropertiesChanged(): Int {
            val changed = mapOf(
                "state" to org.freedesktop.dbus.types.Variant<Any>("updated")
            )
            connection.sendMessage(
                PropertiesSignalPeer.PropertiesChanged(
                    path = path,
                    interfaceName = PHASE3_INTERFACE,
                    changedProperties = changed,
                    invalidatedProperties = emptyList()
                )
            )
            return 1
        }

        override fun getObjectPath(): String = path
    }

    @DBusInterfaceName("org.freedesktop.DBus.Properties")
    interface PropertiesSignalPeer : DBusInterface {
        @DBusMemberName("PropertiesChanged")
        class PropertiesChanged(
            path: String,
            val interfaceName: String,
            val changedProperties: Map<String, org.freedesktop.dbus.types.Variant<Any>>,
            val invalidatedProperties: List<String>
        ) : DBusSignal(path, interfaceName, changedProperties, invalidatedProperties)
    }

    companion object {
        private const val CROSS_RUNTIME_ENABLED_PROP = "kdbus.crossRuntimeInterop.enabled"
        private const val CROSS_RUNTIME_REVERSE_ENABLED_PROP =
            "kdbus.crossRuntimeInterop.reverse.enabled"
        private const val PHASE3_INTERFACE = "org.monkopedia.sdbus.phase3"
        private const val CREATE_PIPE_READ_FD_METHOD = "CreatePipeReadFd"
        private const val STORE_PIPE_READ_FD_METHOD = "StorePipeReadFd"
        private const val VERIFY_STORED_PIPE_READ_FD_METHOD = "VerifyStoredPipeReadFd"
        private const val ROUND_TRIP_LARGE_MAP_METHOD = "RoundTripLargeMap"
        private const val ROUND_TRIP_NESTED_VARIANT_METHOD = "RoundTripNestedVariant"
        private const val ROUND_TRIP_MIXED_PAYLOAD_METHOD = "RoundTripMixedPayload"
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

    private fun isValidNestedPayload(payload: Map<String, List<Int>>, expectedSize: Int): Boolean {
        if (payload.size != expectedSize) return false
        return payload.all { (key, values) ->
            val index = key.removePrefix("key-").toIntOrNull() ?: return@all false
            values == listOf(index, index + 1, index * 2)
        }
    }
}
