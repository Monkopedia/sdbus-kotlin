package com.monkopedia.sdbus

import com.sun.security.auth.module.UnixSystem
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
 * as non-throwing values (euid/egid equal uid/gid for a non-setuid process); [Message.seLinuxContext]
 * is host-dependent — a label where SELinux is enforcing, or a thrown [SdbusException] otherwise.
 * The native backend covers the same surface against the live POSIX identity in
 * CredentialsIntegrationTest.
 */
class JvmCredentialsTest {

    private fun connectOrNull(connect: () -> Connection): Connection? = try {
        connect()
    } catch (e: Exception) {
        null
    }

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

        val serverConnection = connectOrNull { createSessionBusConnection(service) }
            ?: return@runBlocking
        val proxyConnection = connectOrNull { createSessionBusConnection() } ?: run {
            serverConnection.release()
            return@runBlocking
        }

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

            // Resolved to this same process. euid/egid equal the real uid/gid for a non-setuid
            // process, which the test runner is.
            assertEquals(expectedPid, withTimeout(2_000) { pidSeen.await() })
            assertEquals(expectedUid, withTimeout(2_000) { uidSeen.await() })
            assertEquals(expectedUid, withTimeout(2_000) { euidSeen.await() })
            assertEquals(expectedGid, withTimeout(2_000) { gidSeen.await() })
            assertEquals(expectedGid, withTimeout(2_000) { egidSeen.await() })
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
