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
 * backend. The bus stamps the authoritative sender on an emitted signal and the receiver resolves
 * the sender's credentials (filled from the local process for a same-process sender). The JVM
 * backend resolves pid/uid/euid/gid/egid/supplementary-gids; only [Message.seLinuxContext] is
 * unavailable over junixsocket and must throw the documented [SdbusException] (the native backend
 * covers SELinux's happy path, where labelled, in CredentialsIntegrationTest).
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
        val seLinuxThrewSdbus = CompletableDeferred<Boolean>()

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
                // SELinux context is unavailable over junixsocket — must raise the library's
                // SdbusException specifically, not return a bogus value or some unrelated error.
                seLinuxThrewSdbus.complete(
                    runCatching { message.seLinuxContext }.exceptionOrNull() is SdbusException
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
            assertTrue(withTimeout(2_000) { seLinuxThrewSdbus.await() })
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
