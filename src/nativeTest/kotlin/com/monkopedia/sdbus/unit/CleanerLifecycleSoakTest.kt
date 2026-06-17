@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, NativeRuntimeApi::class)

package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.posix.usleep

/**
 * Real-connection lifecycle soak: proves that tearing a live served object / connection down actually
 * lets it be **collected** — i.e. the library's internal bookkeeping does not leak it so that its GC
 * cleaner can never fire.
 *
 * Why [CleanerSoakTest] is not enough: that suite builds resources in isolation and abandons them, so
 * nothing in the live system ever holds them — it proves the cleaner *mechanism* works, but not that
 * the live system *lets go*. This suite found two real leaks (fixed alongside it):
 *  1. `VTable.clear()` did not drop the registered method/property/signal callbacks, which capture the
 *     adaptor — so every served object leaked after `release()`.
 *  2. Several cleanup closures captured `this@ConnectionImpl` (via member access like `sdbus.…`),
 *     laundered through `Reference`'s `onLeaveScopes` so the compiler's non-capturing `createCleaner`
 *     check could not catch it — pinning the whole connection.
 *
 * The adaptor below registers a method (captures `this` via `asyncCall`), a property getter (captures
 * `this`), a signal, and an object manager — exercising the registration paths that leaked. The test
 * does the full cycle against a real bus: create -> register -> tear down -> drop refs -> force GC ->
 * assert collected. A failure here is a genuine leak, not a flaky cleaner.
 *
 * Needs a real D-Bus session bus (run under `dbus-run-session`) and is probabilistic, so — like
 * [CleanerSoakTest] — it is excluded from the default test run behind the `-PgcSoak` gate and runs
 * only on the nightly schedule.
 */
class CleanerLifecycleSoakTest {

    private val attempts = 600
    private val iface = InterfaceName("org.sdbuskotlin.soak.Iface")
    private val path = ObjectPath("/org/sdbuskotlin/soak")
    private val peer = ServiceName("org.sdbuskotlin.soak.Peer")

    /**
     * A minimal adaptor whose registrations capture `this` exactly the way generated adaptors do:
     * `asyncCall(this::ping)` for the method and `withGetter { value }` for the property. `release()` must
     * drop every one of those so the adaptor (and the object, and the connection) can be collected.
     */
    private class SoakAdaptor(connection: Connection, path: ObjectPath, iface: InterfaceName) {
        private val obj: Object = createObject(connection, path)
        private var vtable: Resource? = null
        private var objManager: Resource? = null
        private var value: ULong = 0u

        fun register(iface: InterfaceName) {
            objManager = obj.addObjectManager()
            vtable = obj.addVTable(iface) {
                method(MethodName("Ping")) {
                    asyncCall(this@SoakAdaptor::ping)
                }
                prop(PropertyName("Value")) {
                    withGetter { value }
                }
                signal(SignalName("Tick")) {
                    with<ULong>("count")
                }
            }
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun ping(): ULong = value++

        fun release() {
            vtable?.release()
            vtable = null
            objManager?.release()
            objManager = null
            obj.release()
        }
    }

    @Test
    fun registeredObjectIsCollectedAfterRelease() {
        val conn = createBusConnection()
        try {
            assertCollects("registered Object after release()", setUpAndReleaseObject(conn))
        } finally {
            conn.release()
        }
    }

    @Test
    fun connectionIsCollectedAfterServingAndRelease() {
        assertCollects("Connection after serving + release()", setUpAndReleaseConnection())
    }

    @Test
    fun proxyIsCollectedAfterSignalSubscriptionAndRelease() {
        val conn = createBusConnection()
        try {
            assertCollects("Proxy after signal subscription", setUpAndReleaseProxy(conn))
        } finally {
            conn.release()
        }
    }

    @Test
    fun connectionIsCollectedAfterProxySignalSubscription() {
        // Exercises ConnectionImpl.registerSignalHandler: its slot Reference cleanup closure used to
        // capture this@ConnectionImpl, pinning the connection for the subscription's lifetime.
        assertCollects("Connection after proxy subscription", setUpAndReleaseProxyConnection())
    }

    /** Subscribe a signal on a proxy, then tear the proxy down; the proxy must be collectable. */
    private fun setUpAndReleaseProxy(conn: Connection): WeakReference<Any> {
        val proxy = createProxy(conn, peer, path, runEventLoopThread = false)
        proxy.onSignal(iface, SignalName("Tick")) { call { _: ULong -> } }.release()
        proxy.release()
        return WeakReference(proxy)
    }

    private fun setUpAndReleaseProxyConnection(): WeakReference<Any> {
        val conn = createBusConnection()
        val proxy = createProxy(conn, peer, path, runEventLoopThread = false)
        proxy.onSignal(iface, SignalName("Tick")) { call { _: ULong -> } }.release()
        proxy.release()
        conn.release()
        return WeakReference(conn)
    }

    /** Frame returns a weak ref only; the adaptor is unreachable on return once released. */
    private fun setUpAndReleaseObject(conn: Connection): WeakReference<Any> {
        val adaptor = SoakAdaptor(conn, path, iface)
        adaptor.register(iface)
        adaptor.release()
        return WeakReference(adaptor)
    }

    /**
     * Opens a connection, starts its event loop, registers and releases a served object on it, then
     * releases the connection — which must leave nothing pinning the connection.
     */
    private fun setUpAndReleaseConnection(): WeakReference<Any> {
        val conn = createBusConnection()
        conn.startEventLoop()
        val adaptor = SoakAdaptor(conn, path, iface)
        adaptor.register(iface)
        adaptor.release()
        runBlocking { conn.stopEventLoop() }
        conn.release()
        return WeakReference(conn)
    }

    private fun assertCollects(label: String, weak: WeakReference<Any>) {
        repeat(attempts) {
            GC.collect()
            usleep(5_000u)
            if (weak.value == null) return
        }
        fail(
            "$label was NOT collected after forced GC — the live system still holds a strong " +
                "reference (an uncleared callback or a cleanup closure capturing the owner), so " +
                "the GC cleaner can never fire. This is a resource leak."
        )
    }
}
