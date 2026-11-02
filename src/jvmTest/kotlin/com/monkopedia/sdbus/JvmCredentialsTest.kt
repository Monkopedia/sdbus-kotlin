package com.monkopedia.sdbus

import com.sun.security.auth.module.UnixSystem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * External (real-bus) coverage for the sender-credential surface on [Message] over the JVM wire
 * backend, read from a received signal whose sender is this same process (so the credentials
 * resolve to the running process). The JVM backend yields pid/uid/euid/gid/egid/supplementary-gids
 * as non-throwing values; [Message.seLinuxContext] is host-dependent — a label where SELinux is
 * enforcing, or a thrown [SdbusException] otherwise. The native backend covers the same surface
 * against the live POSIX identity in CredentialsIntegrationTest.
 *
 * The euid/egid expectations are read from `/proc/self/status` rather than from
 * `com.sun.security.auth.module.UnixSystem`, which reports `getuid()`/`getgid()` — the REAL ids.
 * On the test runner the two agree, so only a process whose effective identity differs from its
 * real one can tell them apart; asserting against the real ids would re-encode the very
 * substitution issue #247 removed, and would pass no matter which column the backend read.
 *
 * Needs a real session bus, and every job that runs it wraps the build in `dbus-run-session`, so an
 * unreachable bus is a broken environment: the factory's throw is left to FAIL the test rather than
 * swallowed into an early return that JUnit would record as a pass (issue #227).
 */
class JvmCredentialsTest {

    // Read independently of the backend's own parser, so this is an oracle rather than a mirror.
    // The Uid:/Gid: lines are real, effective, saved-set, filesystem.
    private fun effectiveIdOfThisProcess(prefix: String): UInt =
        File("/proc/self/status").readLines()
            .first { it.startsWith(prefix) }
            .split('\t', ' ')
            .filter(String::isNotEmpty)[2]
            .toUInt()

    @Test
    fun receivedSignalResolvesSupportedCredentialsAndRejectsUnsupported() = runBlocking {
        val suffix = "creds${System.nanoTime()}"
        val service = ServiceName("com.monkopedia.sdbus.jvmcreds.$suffix")
        val objectPath = ObjectPath("/com/monkopedia/sdbus/jvmcreds$suffix")
        val interfaceName = InterfaceName("com.monkopedia.sdbus.jvmcreds.$suffix.Interface")
        val signalName = SignalName("Ping")
        val expectedPid = ProcessHandle.current().pid().toInt()
        val unix = UnixSystem()
        val expectedUid = unix.uid.toUInt()
        val expectedGid = unix.gid.toUInt()
        val expectedEuid = effectiveIdOfThisProcess("Uid:")
        val expectedEgid = effectiveIdOfThisProcess("Gid:")

        val serverConnection = createSessionBusConnection(service)
        val proxyConnection = createSessionBusConnection()

        val pidSeen = CompletableDeferred<Int>()
        val uidSeen = CompletableDeferred<UInt>()
        val euidSeen = CompletableDeferred<UInt>()
        val gidSeen = CompletableDeferred<UInt>()
        val egidSeen = CompletableDeferred<UInt>()
        val supplementaryReadable = CompletableDeferred<Boolean>()
        val seLinuxDefiniteOutcome = CompletableDeferred<Boolean>()

        val obj = createObject(serverConnection, objectPath)
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, service, objectPath)
        val signalRegistration = proxy.registerSignalHandler(interfaceName, signalName) { message ->
            if (!pidSeen.isCompleted) {
                pidSeen.complete(message.credsPid)
                uidSeen.complete(message.credsUid)
                euidSeen.complete(message.credsEuid)
                gidSeen.complete(message.credsGid)
                egidSeen.complete(message.credsEgid)
                supplementaryReadable.complete(
                    runCatching { message.credsSupplementaryGids }.isSuccess
                )
                // SELinux context is host-dependent: a label where SELinux is enforcing, or a
                // thrown SdbusException where it is unavailable (e.g. the typical junixsocket/CI
                // case, which reports AccessDenied). Either is a valid contract outcome; what must
                // NOT happen is a leaked non-SdbusException. (Observed locally: AccessDenied.)
                seLinuxDefiniteOutcome.complete(
                    runCatching { message.seLinuxContext }
                        .fold({ true }, { it is SdbusException })
                )
            }
        }

        try {
            val signal = obj.createSignal(interfaceName, signalName)
            signal.append(1)
            signal.send()

            // Resolved to this same process, so each id must match the identity the kernel
            // reports for it — the effective ones read as effective, not as a copy of the real
            // ones (issue #247).
            assertEquals(expectedPid, withTimeout(2_000) { pidSeen.await() })
            assertEquals(expectedUid, withTimeout(2_000) { uidSeen.await() })
            assertEquals(
                expectedEuid,
                withTimeout(2_000) { euidSeen.await() },
                "Message.credsEuid must be this process's EFFECTIVE uid (/proc/self/status " +
                    "column 2 = $expectedEuid), not its real uid ($expectedUid)"
            )
            assertEquals(expectedGid, withTimeout(2_000) { gidSeen.await() })
            assertEquals(
                expectedEgid,
                withTimeout(2_000) { egidSeen.await() },
                "Message.credsEgid must be this process's EFFECTIVE gid (/proc/self/status " +
                    "column 2 = $expectedEgid), not its real gid ($expectedGid)"
            )
            assertTrue(withTimeout(2_000) { supplementaryReadable.await() })
            // seLinuxContext yielded a label or the documented SdbusException — never a leaked
            // unrelated exception type.
            assertTrue(withTimeout(2_000) { seLinuxDefiniteOutcome.await() })
        } finally {
            signalRegistration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
