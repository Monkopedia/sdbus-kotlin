@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.posix.getegid
import platform.posix.geteuid
import platform.posix.getgid
import platform.posix.getpid
import platform.posix.getuid

/**
 * External (real-bus) coverage for the sender-credential surface on [com.monkopedia.sdbus.Message]
 * (`credsPid`/`credsUid`/`credsEuid`/`credsGid`/`credsEgid`/`credsSupplementaryGids`/
 * `seLinuxContext`), read from inside a served method handler via
 * [com.monkopedia.sdbus.Object.currentlyProcessedMessage]. The caller is a second connection in
 * this same process, so sd-bus resolves the credentials to this process and they can be checked
 * against the live POSIX identity. Native-only: euid/egid/SELinux are not implemented on the JVM
 * backend (covered there by JvmCredentialsTest).
 */
class CredentialsIntegrationTest {

    private class CapturedCredentials {
        var pid: Int? = null
        var uid: UInt? = null
        var euid: UInt? = null
        var gid: UInt? = null
        var egid: UInt? = null
        var supplementaryGidsReadable: Boolean = false

        // sd_bus_creds_get_selinux_context fails (throws) on a host without SELinux labelling, and
        // succeeds with a non-empty label where SELinux is active. Both are valid contract outcomes.
        var seLinuxReadOutcome: Boolean? = null
    }

    @Test
    fun servedMethodSeesCallerCredentials() {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.creds$id")
        val path = ObjectPath("/com/monkopedia/sdbus/creds$id")
        val iface = InterfaceName("com.monkopedia.sdbus.creds$id.Interface")
        val methodName = MethodName("Whoami")
        val captured = CapturedCredentials()

        val serverConnection = createBusConnection(service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, path)
        val registration = obj.addVTable(iface) {
            method(methodName) {
                call { token: Int ->
                    val message = obj.currentlyProcessedMessage
                    captured.pid = message.credsPid
                    captured.uid = message.credsUid
                    captured.euid = message.credsEuid
                    captured.gid = message.credsGid
                    captured.egid = message.credsEgid
                    captured.supplementaryGidsReadable = runCatching {
                        message.credsSupplementaryGids
                    }.isSuccess
                    captured.seLinuxReadOutcome = try {
                        message.seLinuxContext.isNotEmpty()
                    } catch (e: SdbusException) {
                        // No SELinux labelling on this host — the call path still executed.
                        false
                    }
                    token
                }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, service, path, runEventLoopThread = false)

        try {
            assertEquals(7, proxy.callMethod<Int>(iface, methodName) { call(7) })

            // The caller is this process (a second bus connection), so the bus-resolved
            // credentials must match the live POSIX identity.
            assertEquals(getpid(), captured.pid)
            assertEquals(getuid(), captured.uid)
            assertEquals(geteuid(), captured.euid)
            assertEquals(getgid(), captured.gid)
            assertEquals(getegid(), captured.egid)
            // Supplementary GIDs may be an empty list, but the accessor must succeed.
            assertTrue(captured.supplementaryGidsReadable)
            // seLinuxContext must have produced a definite outcome (value or documented throw).
            assertTrue(captured.seLinuxReadOutcome != null)
        } finally {
            runBlocking { serverConnection.stopEventLoop() }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
