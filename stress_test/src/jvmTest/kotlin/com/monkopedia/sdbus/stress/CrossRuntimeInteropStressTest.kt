package com.monkopedia.sdbus.stress

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DirectConnectionBuilder
import org.freedesktop.dbus.connections.transports.TransportBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface

class CrossRuntimeInteropStressTest {
    @Test
    fun jvmClientInvokesNativePeerRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        runRepeatedCase("jvm-client-increment") { iteration ->
            runJvmToNativeIncrementCase(iteration)
        }
    }

    @Test
    fun nativeClientInvokesJvmPeerRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        runRepeatedCase("native-client-increment") { iteration ->
            runNativeToJvmIncrementCase(iteration)
        }
    }

    @Test
    fun jvmClientTimesOutAgainstSlowNativePeerRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        runRepeatedCase("jvm-client-timeout") { iteration ->
            runJvmToNativeIncrementCase(
                iteration = iteration,
                callTimeoutMs = 100,
                serverDelayMs = 700,
                expectedFailureContains = "time"
            )
        }
    }

    @Test
    fun nativeClientTimesOutAgainstSlowJvmPeerRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        runRepeatedCase("native-client-timeout") { iteration ->
            runNativeToJvmIncrementCase(
                iteration = iteration,
                serverDelayMs = 700,
                callTimeoutMs = 100,
                expectedNativeFailureContains = "time"
            )
        }
    }

    @Test
    fun jvmClientCancelsAsyncCallBeforeReplyRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        runRepeatedCase("jvm-client-cancel-before") { iteration ->
            runJvmToNativeCancellationCase(
                iteration = iteration,
                cancelAfterMs = 50
            )
        }
    }

    @Test
    fun jvmClientCancelsAsyncCallDuringReplyRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        runRepeatedCase("jvm-client-cancel-during") { iteration ->
            runJvmToNativeCancellationCase(
                iteration = iteration,
                cancelAfterMs = 700
            )
        }
    }

    @Test
    fun jvmClientObservesNativeErrorRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        runRepeatedCase("jvm-client-error") { iteration ->
            val errorMessage = "StressNativeError-$iteration"
            runJvmToNativeIncrementCase(
                iteration = iteration,
                serverFailMessage = errorMessage,
                expectedFailureContains = errorMessage
            )
        }
    }

    @Test
    fun nativeClientObservesJvmErrorRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        runRepeatedCase("native-client-error") { iteration ->
            val errorMessage = "StressJvmError-$iteration"
            runNativeToJvmIncrementCase(
                iteration = iteration,
                serverFailMessage = errorMessage,
                expectedNativeFailureContains = errorMessage
            )
        }
    }

    @Test
    fun jvmClientFailsWhenNativePeerDropsInFlightRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return

        runRepeatedCase("jvm-client-drop") { iteration ->
            runJvmToNativeDropInFlightCase(iteration)
        }
    }

    @Test
    fun nativeClientFailsWhenJvmPeerDropsInFlightRepeatedly() {
        if (System.getProperty(CROSS_RUNTIME_ENABLED_PROP) != "true") return
        if (System.getProperty(CROSS_RUNTIME_REVERSE_ENABLED_PROP) != "true") return

        runRepeatedCase("native-client-drop") { iteration ->
            runNativeToJvmIncrementCase(
                iteration = iteration,
                serverDelayMs = 5_000,
                callTimeoutMs = 5_000,
                expectNativeFailure = true,
                dropConnectionAfterMs = 150
            )
        }
    }

    private fun runRepeatedCase(caseName: String, block: (iteration: Int) -> Unit) {
        if (!isCaseSelected(caseName)) return
        repeat(repeatCount()) { index ->
            block(index + 1)
        }
    }

    private fun runJvmToNativeIncrementCase(
        iteration: Int,
        callTimeoutMs: Long = 5_000L,
        serverDelayMs: Long = 0L,
        serverFailMessage: String? = null,
        expectedFailureContains: String? = null
    ) {
        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-stress-jvm-native-$iteration-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val methodName = INCREMENT_METHOD
        val objectPath = PHASE3_OBJECT_PATH
        val expectedArg = 50 + iteration
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
            if (serverDelayMs > 0) {
                builder.environment()["KDBUS_INTEROP_DELAY_MS"] = serverDelayMs.toString()
            }
            serverFailMessage?.let { message ->
                builder.environment()["KDBUS_INTEROP_FAIL_MESSAGE"] = message
            }
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "stress-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis()),
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
                fun invokeIncrement(): Int = proxy.callMethod<Int>(
                    InterfaceName(interfaceName),
                    MethodName(methodName)
                ) {
                    timeout = callTimeoutMs.milliseconds
                    call(expectedArg)
                }

                if (expectedFailureContains != null) {
                    val failure = runCatching { invokeIncrement() }.exceptionOrNull()
                    assertTrue(failure != null, "Expected call failure from native peer")
                    val message = failure.message ?: failure.toString()
                    assertTrue(
                        message.contains(expectedFailureContains, ignoreCase = true),
                        "Expected failure containing '$expectedFailureContains' but was '$message'"
                    )
                } else {
                    val result = retryCall(timeoutMillis()) { invokeIncrement() }
                    assertEquals(expectedArg + 1, result)
                }
            } finally {
                proxy.release()
                connection.release()
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(timeoutSeconds(), TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(0, process.exitValue(), "Native peer failed. Output:\n$nativeLog")
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    private fun runJvmToNativeDropInFlightCase(iteration: Int) {
        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-stress-jvm-drop-$iteration-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val methodName = INCREMENT_METHOD
        val objectPath = PHASE3_OBJECT_PATH
        val expectedArg = 300 + iteration
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
            builder.environment()["KDBUS_INTEROP_DELAY_MS"] = "5000"
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "stress-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis()),
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
                val killer = thread(start = true, isDaemon = true, name = "stress-native-killer") {
                    Thread.sleep(150)
                    process.destroyForcibly()
                }
                val failure = runCatching {
                    proxy.callMethod<Int>(
                        InterfaceName(interfaceName),
                        MethodName(methodName)
                    ) {
                        timeout = 5_000.milliseconds
                        call(expectedArg)
                    }
                }.exceptionOrNull()
                killer.join(1_000)
                assertTrue(
                    failure != null,
                    "Expected call failure when native peer drops in-flight"
                )
            } finally {
                proxy.release()
                connection.release()
            }
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    private fun runJvmToNativeCancellationCase(iteration: Int, cancelAfterMs: Long) {
        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-stress-jvm-cancel-$iteration-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val methodName = INCREMENT_METHOD
        val objectPath = PHASE3_OBJECT_PATH
        val expectedArg = 400 + iteration
        val recoveryArg = expectedArg + 1
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
            builder.environment()["KDBUS_INTEROP_EXPECT_CALL_COUNT"] = "2"
            builder.environment()["KDBUS_INTEROP_DELAY_MS"] = "1200"
            builder.redirectErrorStream(true)
        }.start()
        val outputPump = thread(start = true, isDaemon = true, name = "stress-native-output") {
            process.inputStream.use { input -> input.copyTo(nativeOutput) }
        }

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis()),
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
                val cancellationFailure = runCatching {
                    runBlocking {
                        withTimeout(cancelAfterMs) {
                            proxy.callMethodAsync<Int>(
                                InterfaceName(interfaceName),
                                MethodName(methodName)
                            ) {
                                timeout = 5_000.milliseconds
                                call(expectedArg)
                            }
                        }
                    }
                }.exceptionOrNull()
                assertTrue(
                    cancellationFailure is TimeoutCancellationException,
                    "Expected TimeoutCancellationException but was $cancellationFailure"
                )

                val recovery = retryCall(timeoutMillis()) {
                    proxy.callMethod<Int>(
                        InterfaceName(interfaceName),
                        MethodName(methodName)
                    ) {
                        timeout = 5_000.milliseconds
                        call(recoveryArg)
                    }
                }
                assertEquals(recoveryArg + 1, recovery)
            } finally {
                proxy.release()
                connection.release()
            }

            val nativeLog = nativeOutput.toString(Charsets.UTF_8)
            assertTrue(
                process.waitFor(timeoutSeconds(), TimeUnit.SECONDS),
                "Native peer did not exit in time. Output:\n$nativeLog"
            )
            assertEquals(0, process.exitValue(), "Native peer failed. Output:\n$nativeLog")
        } finally {
            process.destroyForcibly()
            outputPump.join(1_000)
            Files.deleteIfExists(socketPath)
        }
    }

    private fun runNativeToJvmIncrementCase(
        iteration: Int,
        serverDelayMs: Long = 0L,
        serverFailMessage: String? = null,
        callTimeoutMs: Long = 5_000L,
        expectNativeFailure: Boolean = false,
        expectedNativeFailureContains: String? = null,
        dropConnectionAfterMs: Long? = null
    ) {
        val kexe = nativeTestBinaryPath()
        assertTrue(Files.isExecutable(kexe), "Native test binary not found: $kexe")
        val socketPath = Files.createTempFile("kdbus-stress-native-jvm-$iteration-", ".sock")
        Files.deleteIfExists(socketPath)

        val interfaceName = PHASE3_INTERFACE
        val methodName = INCREMENT_METHOD
        val objectPath = PHASE3_OBJECT_PATH
        val expectedArg = 100 + iteration
        val observedArg = AtomicInteger(Int.MIN_VALUE)
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
            Phase3ServerObject(
                path = objectPath,
                observedArg = observedArg,
                invoked = invoked,
                delayMs = serverDelayMs,
                failMessage = serverFailMessage
            )
        )
        val listenFailure = AtomicReference<Throwable?>(null)
        val listenThread = thread(
            start = true,
            isDaemon = true,
            name = "stress-jvm-direct-listen"
        ) {
            runCatching { connection.listen() }
                .onFailure(listenFailure::set)
        }
        fun listenFailureDetails(): String = listenFailure.get()?.stackTraceToString() ?: "none"

        val nativeOutput = ByteArrayOutputStream()
        var process: Process? = null
        var outputPump: Thread? = null

        try {
            assertTrue(
                waitForSocket(socketPath, timeoutMillis()),
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
                procBuilder.environment()["KDBUS_INTEROP_TIMEOUT_MS"] = callTimeoutMs.toString()
                val shouldExpectFailure = expectNativeFailure ||
                    expectedNativeFailureContains != null
                if (shouldExpectFailure) {
                    procBuilder.environment()["KDBUS_INTEROP_EXPECT_ERROR"] = "true"
                    expectedNativeFailureContains?.let { contains ->
                        procBuilder.environment()["KDBUS_INTEROP_EXPECT_ERROR_CONTAINS"] = contains
                    }
                }
                procBuilder.environment()["KDBUS_INTEROP_CONNECT_FD"] = "true"
                procBuilder.environment()["KDBUS_INTEROP_USE_ANONYMOUS_AUTH"] = "true"
                procBuilder.redirectErrorStream(true)
            }.start()
            process = launchedProcess
            outputPump = thread(start = true, isDaemon = true, name = "stress-native-output") {
                launchedProcess.inputStream.use { input -> input.copyTo(nativeOutput) }
            }
            dropConnectionAfterMs?.let { delayMs ->
                Thread.sleep(delayMs)
                runCatching { connection.disconnect() }
            }

            assertTrue(
                launchedProcess.waitFor(timeoutSeconds(), TimeUnit.SECONDS),
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
            val shouldExpectFailure = expectNativeFailure || expectedNativeFailureContains != null
            if (!shouldExpectFailure) {
                assertTrue(
                    invoked.await(timeoutSeconds(), TimeUnit.SECONDS),
                    "JVM server did not observe native invocation " +
                        "for address '$connectionAddress'. " +
                        "Listen failure:\n${listenFailureDetails()}\nOutput:\n$nativeLog"
                )
                assertEquals(expectedArg, observedArg.get())
            }
        } finally {
            process?.destroyForcibly()
            outputPump?.join(1_000)
            runCatching { connection.disconnect() }
            listenThread.join(1_000)
            restoreSaslCollator()
            Files.deleteIfExists(socketPath)
        }
    }

    private fun repeatCount(): Int =
        System.getProperty(STRESS_REPEAT_PROP)?.toIntOrNull()?.coerceAtLeast(1) ?: 25

    private fun timeoutMillis(): Long =
        System.getProperty(STRESS_TIMEOUT_MS_PROP)?.toLongOrNull()?.coerceAtLeast(1_000L)
            ?: 30_000L

    private fun timeoutSeconds(): Long = (timeoutMillis() / 1_000L).coerceAtLeast(5L)

    private fun isCaseSelected(caseName: String): Boolean {
        val selected = System.getProperty(STRESS_CASE_FILTER_PROP)?.trim().orEmpty()
        if (selected.isEmpty()) return true
        return caseName.contains(selected, ignoreCase = true)
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

    @DBusInterfaceName(PHASE3_INTERFACE)
    interface Phase3ServerInterface : DBusInterface {
        @DBusMemberName(INCREMENT_METHOD)
        fun increment(value: Int): Int
    }

    class Phase3ServerObject(
        private val path: String,
        private val observedArg: AtomicInteger,
        private val invoked: CountDownLatch,
        private val delayMs: Long = 0L,
        private val failMessage: String? = null
    ) : Phase3ServerInterface {
        override fun increment(value: Int): Int {
            observedArg.set(value)
            invoked.countDown()
            if (delayMs > 0) Thread.sleep(delayMs)
            failMessage?.let { message -> throw DBusExecutionException(message) }
            return value + 1
        }

        override fun getObjectPath(): String = path
    }

    companion object {
        private const val CROSS_RUNTIME_ENABLED_PROP = "kdbus.crossRuntimeInterop.enabled"
        private const val CROSS_RUNTIME_REVERSE_ENABLED_PROP =
            "kdbus.crossRuntimeInterop.reverse.enabled"
        private const val STRESS_REPEAT_PROP = "kdbus.stress.repeat"
        private const val STRESS_TIMEOUT_MS_PROP = "kdbus.stress.timeout.ms"
        private const val STRESS_CASE_FILTER_PROP = "kdbus.stress.caseFilter"

        private const val PHASE3_INTERFACE = "org.monkopedia.sdbus.phase3"
        private const val PHASE3_OBJECT_PATH = "/org/monkopedia/sdbus/phase3/Object"
        private const val INCREMENT_METHOD = "Increment"
    }
}
