@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, NativeRuntimeApi::class)

package com.monkopedia.sdbus.unit

import cnames.structs.sd_bus
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.internal.ConnectionImpl
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.defaultConnection
import com.monkopedia.sdbus.internal.ConnectionImpl.Companion.pseudoConnection
import com.monkopedia.sdbus.internal.SdBus
import com.monkopedia.sdbus.mocks.DefaultResponses.withDefaults
import com.monkopedia.sdbus.mocks.MappingHandler
import com.monkopedia.sdbus.mocks.MappingHandler.Companion.configure
import com.monkopedia.sdbus.mocks.RecordingHandler
import com.monkopedia.sdbus.mocks.SdBusMock
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import platform.posix.EEXIST
import platform.posix.EINVAL
import platform.posix.ENOMEM
import platform.posix._SC_PAGESIZE
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.sysconf
import sdbus.sd_bus_vtable

/**
 * The three native error-path defects of #276, each driven down its failure branch:
 * the pseudo bus's ignored `sd_bus_start` result, the `StableRef` leaked by every
 * registration that fails, and the `sd_bus` leaked by the hand-rolled `sd_bus_open_*` wrappers.
 */
internal class ErrorPathLeakTest {

    private val mock = SdBusMock().withDefaults()

    // --- 1. openPseudoBus must check sd_bus_start's own result -----------------------------

    @Test
    fun pseudoBusThrowsAndUnrefsWhenStartFailsUnexpectedly() {
        mock.configure { method(SdBusMock::sd_bus_start) returns -ENOMEM }
        val calls = recordingCalls(mock)

        val error = assertFailsWith<SdbusException> { pseudoConnection(mock) }

        assertTrue("Failed to start pseudo bus" in error.message.orEmpty(), "$error")
        assertEquals(1, calls().count { it == "sd_bus_close_unref" }, "calls: ${calls()}")
    }

    @Test
    fun pseudoBusToleratesTheExpectedEinvalFromStart() {
        mock.configure { method(SdBusMock::sd_bus_start) returns -EINVAL }
        val calls = recordingCalls(mock)

        val connection = pseudoConnection(mock)
        val openCalls = calls()
        connection.release()

        assertEquals(listOf("sd_bus_new", "sd_bus_start"), openCalls)
    }

    // --- 2. A failed registration must not leave its StableRef as a permanent GC root ------

    @Test
    fun stableRefPinsItsTargetUntilDisposed() {
        // Positive control for the probe below: it must see a live StableRef as a leak.
        val (target, ref) = pinnedTarget()
        assertFalse(isCollected(target), "a live StableRef should keep its target alive")
        ref.dispose()
        assertTrue(isCollected(target), "a disposed StableRef should release its target")
    }

    @Test
    fun failedAddMatchDisposesItsStableRef() = assertNoStableRefLeak(
        failing = { method(SdBusMock::sd_bus_add_match) returns -EINVAL }
    ) { connection, userData -> connection.addMatch("type='signal'") { userData.hashCode() } }

    @Test
    fun failedAddObjectVTableDisposesItsStableRef() = assertNoStableRefLeak(
        failing = { method(SdBusMock::sd_bus_add_object_vtable) returns -EEXIST }
    ) { connection, userData ->
        connection.addObjectVTable(
            ObjectPath("/org/example/leak"),
            InterfaceName("org.example.Leak"),
            1L.toCPointer<sd_bus_vtable>()!!,
            userData
        )
    }

    @Test
    fun failedRegisterSignalHandlerDisposesItsStableRef() = assertNoStableRefLeak(
        failing = { method(SdBusMock::sd_bus_match_signal) returns -EINVAL }
    ) { connection, userData ->
        connection.registerSignalHandler(
            "",
            "/org/example/leak",
            "org.example.Leak",
            "Changed",
            ConnectionImpl.sdbus_match_callback,
            userData
        )
    }

    @Test
    fun failedAsyncSendDisposesItsStableRef() = assertNoStableRefLeak(
        failing = { method(SdBusMock::sd_bus_call_async) returns -EINVAL }
    ) { _, userData ->
        MethodCall(mock).send(ConnectionImpl.sdbus_match_callback, userData, 0u)
    }

    /**
     * A leaked StableRef is a permanent GC root, so the userData handed to a failed registration
     * stays reachable forever: the weak reference to it never clears, however often GC runs.
     */
    private fun assertNoStableRefLeak(
        failing: MappingHandler.() -> Unit,
        register: (ConnectionImpl, Any) -> Unit
    ) {
        mock.configure {
            method(SdBusMock::sd_bus_open) answers { _, args ->
                @Suppress("UNCHECKED_CAST")
                (args[1] as CPointer<CPointerVar<sd_bus>>)[0] = 1L.toCPointer()
                1
            }
            failing()
        }
        val connection = defaultConnection(mock)
        val userData = failRegistration(connection, register)
        connection.release()

        assertTrue(isCollected(userData), "a failed registration left its userData pinned")
    }

    // Separate non-inline frames, so nothing on the test's own stack keeps the target reachable.
    private fun failRegistration(
        connection: ConnectionImpl,
        register: (ConnectionImpl, Any) -> Unit
    ): WeakReference<Any> {
        val userData = Any()
        assertFailsWith<SdbusException> { register(connection, userData) }
        return WeakReference(userData)
    }

    private fun pinnedTarget(): Pair<WeakReference<Any>, StableRef<Any>> {
        val target = Any()
        return WeakReference(target) to StableRef.create(target)
    }

    private fun isCollected(ref: WeakReference<Any>): Boolean {
        repeat(GC_ATTEMPTS) {
            GC.collect()
            if (ref.value == null) return true
        }
        return false
    }

    // --- 3. The sd_bus_open_* wrappers must unref the bus when opening fails ---------------

    @Test
    fun failedOpenUserWithAddressDoesNotLeakTheBus() = assertNoBusLeak { sdBus, ret ->
        sdBus.sd_bus_open_user_with_address(ret, MISSING_SOCKET)
    }

    @Test
    fun failedOpenDirectAddressDoesNotLeakTheBus() = assertNoBusLeak { sdBus, ret ->
        sdBus.sd_bus_open_direct(ret, MISSING_SOCKET)
    }

    @Test
    fun failedOpenDirectFdDoesNotLeakTheBus() = assertNoBusLeak { sdBus, ret ->
        sdBus.sd_bus_open_direct(ret, -1)
    }

    @Test
    fun failedOpenServerDoesNotLeakTheBus() = assertNoBusLeak { sdBus, ret ->
        sdBus.sd_bus_open_server(ret, -1)
    }

    /**
     * Against the real libsystemd there is no seam to count `sd_bus_unref`, so this measures the
     * leak itself: each leaked `sd_bus` is a few KiB of malloc'd state, so [OPEN_ATTEMPTS] failed
     * opens grow RSS by tens of MiB when the wrapper leaks, and by ~nothing when it frees.
     */
    private fun assertNoBusLeak(open: (SdBus, CPointer<CPointerVar<sd_bus>>) -> Int) {
        val sdBus = SdBus()
        fun openRepeatedly(times: Int) = memScoped {
            val ret = alloc<CPointerVar<sd_bus>>().ptr
            repeat(times) {
                val r = open(sdBus, ret)
                check(r < 0) { "expected the open to fail, got $r" }
            }
        }
        openRepeatedly(OPEN_WARMUP)
        val before = residentBytes()
        openRepeatedly(OPEN_ATTEMPTS)
        val grownPerOpen = (residentBytes() - before) / OPEN_ATTEMPTS

        println("RSS growth per failed open: $grownPerOpen bytes")
        assertTrue(
            grownPerOpen < MAX_BYTES_PER_OPEN,
            "RSS grew $grownPerOpen bytes per failed open over $OPEN_ATTEMPTS attempts"
        )
    }

    private fun residentBytes(): Long = memScoped {
        val file = fopen("/proc/self/statm", "r") ?: error("cannot open /proc/self/statm")
        val buffer = allocArray<ByteVar>(256)
        val line = try {
            fgets(buffer, 256, file)?.toKString() ?: error("cannot read /proc/self/statm")
        } finally {
            fclose(file)
        }
        line.trim().split(' ')[1].toLong() * sysconf(_SC_PAGESIZE)
    }

    private fun recordingCalls(mock: SdBusMock): () -> List<Any?> {
        val handler = RecordingHandler(mock.callHandler)
        mock.callHandler = handler
        return { handler.calls.map { it.args[0] } }
    }

    private companion object {
        const val GC_ATTEMPTS = 20
        const val MISSING_SOCKET = "unix:path=/nonexistent/sdbus-kotlin-276"
        const val OPEN_WARMUP = 1_000
        const val OPEN_ATTEMPTS = 20_000
        const val MAX_BYTES_PER_OPEN = 256L
    }
}
