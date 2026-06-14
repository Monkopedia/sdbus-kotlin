@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, NativeRuntimeApi::class)

package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PlainMessage
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.internal.ConnectionImpl
import com.monkopedia.sdbus.internal.InternalConnection.Companion.getPseudoConnectionInstance
import com.monkopedia.sdbus.internal.ObjectImpl
import com.monkopedia.sdbus.internal.ProxyImpl
import com.monkopedia.sdbus.internal.Reference
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EBADF
import platform.posix.F_GETFD
import platform.posix.O_RDONLY
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.open
import platform.posix.usleep

/**
 * GC-soak validation for every Kotlin/Native `createCleaner` site in the library.
 *
 * The deterministic `release()` action of each resource is already covered by the regular unit
 * suites (e.g. [UnixFdCommonTest]). This suite proves the inherently timing-dependent OTHER half:
 * that the GC actually *invokes* each cleaner when its owner becomes unreachable without `release()`.
 *
 * **The failure mode it guards against.** A Kotlin/Native cleaner lambda must not capture `this`: if
 * it does, the cleaner keeps the owner alive and the owner keeps the cleaner alive, forming a cycle
 * that is never collected — so the finalizer never runs and the underlying resource leaks silently.
 * Every site deliberately extracts a *separate* holder (`resource` / `allocs` / `holder`) precisely to
 * avoid that capture. A refactor that reintroduces a `this`-capture is exactly what this suite catches.
 *
 * **Two probe strengths:**
 *  - Where the cleanup has a cheap observable side effect (a closing fd, a callback), we assert the
 *    cleaner *body actually ran* — the strongest signal. Covers all three holder *patterns*:
 *    the `(value, closer)` pair ([UnixFd], [Reference]) and the `FdHolder` ([ConnectionImpl.EventFd]).
 *  - For the heavy sd-bus types ([PlainMessage], [ObjectImpl], [ProxyImpl]) there is no cheap
 *    observable, so we assert *collectability* via a [WeakReference]. A `this`-capture is precisely
 *    what stops collection, so a weak ref that never clears is the direct symptom.
 *
 * These are probabilistic (GC is best-effort), so this suite is **excluded from the default
 * `linuxX64Test` run** (it would risk flaking the PR gate) and runs only on the nightly schedule via
 * `-PgcSoak`. A broken cleaner fails deterministically (nothing ever closes / clears); a healthy one
 * settles within the first GC cycle. All probes are bus-free (they use the pseudo connection), so no
 * `dbus-run-session` is required.
 *
 * If you sabotage any cleaner to prove the test has teeth, see the matching negative-control note in
 * the PR — both styles (body-ran and collectability) were verified to fail when the cleaner is broken.
 */
class CleanerSoakTest {

    private val batch = 64
    private val attempts = 400

    // --- Body-ran probes (strongest): the cleaner actually freed the resource after GC ----------

    @Test
    fun unixFdCleanerClosesAbandonedFds() {
        val fds = IntArray(batch) { open("/dev/null", O_RDONLY) }
        fds.forEach { check(it >= 0) { "open(/dev/null) failed: errno=$errno" } }
        abandonUnixFds(fds)
        assertAllFreed("UnixFd", batch) { fds.count { !isFdOpen(it) } }
    }

    @Test
    fun referenceCleanerInvokesOnLeaveScopes() {
        val freed = atomic(0)
        abandonReferences(freed)
        assertAllFreed("Reference.onLeaveScopes", batch) { freed.value }
    }

    @Test
    fun eventFdCleanerClosesOwnedDescriptor() {
        val fds = IntArray(batch)
        abandonEventFds(fds)
        assertAllFreed("ConnectionImpl.EventFd", batch) { fds.count { it > 0 && !isFdOpen(it) } }
    }

    // --- Collectability probes: a this-capture would keep these alive forever ------------------

    @Test
    fun plainMessageIsCollected() = assertCollected("PlainMessage") {
        PlainMessage.createPlainMessage()
    }

    @Test
    fun objectImplIsCollected() {
        val pseudo = getPseudoConnectionInstance()
        assertCollected("ObjectImpl") {
            ObjectImpl(pseudo, ObjectPath("/org/example/soak"))
        }
    }

    @Test
    fun proxyImplIsCollected() {
        val pseudo = getPseudoConnectionInstance()
        assertCollected("ProxyImpl") {
            ProxyImpl(
                pseudo,
                ServiceName("org.example.Soak"),
                ObjectPath("/org/example/soak"),
                runEventLoopThread = false
            )
        }
    }

    // --- Abandonment frames: separate non-inline frames so wrappers are unreachable on return ---

    private fun abandonUnixFds(fds: IntArray) {
        for (fd in fds) UnixFd.adopt(fd) // intentionally not stored, not released
    }

    private fun abandonReferences(freed: AtomicInt) {
        // each Reference is never stored and never released — only GC can fire its cleaner
        repeat(batch) { Reference(Unit) { freed.incrementAndGet() } }
    }

    private fun abandonEventFds(fds: IntArray) {
        for (i in 0 until batch) {
            val ev = ConnectionImpl.EventFd()
            fds[i] = ev.fd // record the owned fd; drop the EventFd itself
        }
    }

    // --- Shared GC-poll machinery --------------------------------------------------------------

    private fun assertAllFreed(label: String, expected: Int, freedCount: () -> Int) {
        var freed = 0
        repeat(attempts) {
            GC.collect()
            usleep(5_000u) // cleaners run on a separate worker after collection
            freed = freedCount()
            if (freed == expected) return
        }
        if (freed == 0) {
            fail(
                "$label GC cleaner freed 0/$expected abandoned resources after forced GC — the " +
                    "cleaner is NOT firing. A closure likely captures `this` (reference cycle). Broken."
            )
        }
        fail("$label GC cleaner freed only $freed/$expected within the poll budget — incomplete.")
    }

    private fun assertCollected(label: String, factory: () -> Any) {
        val weak = weakOf(factory)
        repeat(attempts) {
            GC.collect()
            usleep(5_000u)
            if (weak.value == null) return
        }
        fail(
            "$label was not collected after forced GC — its cleaner likely captures `this` " +
                "(reference cycle), so GC cleanup never runs and the resource leaks."
        )
    }

    /** Separate frame so the only surviving reference to the produced object is the weak one. */
    private fun weakOf(factory: () -> Any): WeakReference<Any> = WeakReference(factory())

    private fun isFdOpen(fd: Int): Boolean = fcntl(fd, F_GETFD) != -1 || errno != EBADF
}
