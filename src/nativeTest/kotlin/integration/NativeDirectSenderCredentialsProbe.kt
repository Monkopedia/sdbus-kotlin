@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createDirectBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.createServerBusConnection
import com.monkopedia.sdbus.method
import kotlin.test.Test
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import platform.linux.sockaddr_un
import platform.posix.AF_UNIX
import platform.posix.F_SETFD
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.fcntl
import platform.posix.getpid
import platform.posix.getuid
import platform.posix.listen
import platform.posix.memset
import platform.posix.sa_family_tVar
import platform.posix.snprintf
import platform.posix.socket
import platform.posix.umask
import platform.posix.unlink

/**
 * PROBE (not a contract assertion): reports what the NATIVE backend does with sender credentials on
 * a brokerless direct connection, so #199's JVM finding can be compared against it. Prints its
 * measurements; asserts nothing about the values.
 */
class NativeDirectSenderCredentialsProbe {

    @Test
    fun reportNativeDirectConnectionSenderCredentials() {
        val socketPath = "/tmp/sdbus-kotlin-199-probe.sock"
        val path = ObjectPath("/com/monkopedia/sdbus/probe199")
        val iface = InterfaceName("com.monkopedia.sdbus.probe199.Iface")
        val member = MethodName("Ping")

        val listenFd = openUnixSocket(socketPath)
        val context = newFixedThreadPoolContext(4, "probe199")

        var observedSender: String? = "<not-run>"
        var uidOutcome = "<not-run>"
        var pidOutcome = "<not-run>"

        runBlocking {
            var server: com.monkopedia.sdbus.Connection? = null
            val accepting = launch(context) {
                val fd = accept(listenFd, null, null)
                fcntl(fd, F_SETFD, SOCK_CLOEXEC)
                server = createServerBusConnection(UnixFd.adopt(fd))
                server?.startEventLoop()
            }
            val client = createDirectBusConnection("unix:path=$socketPath")
            client.startEventLoop()
            accepting.join()

            val serverConnection = server!!
            val obj = createObject(serverConnection, path)
            val registration = obj.addVTable(iface) {
                method(member) {
                    call { token: Int ->
                        val message = obj.currentlyProcessedMessage
                        observedSender = message.sender?.value
                        uidOutcome = runCatching { message.credsUid.toString() }
                            .getOrElse { "THREW: ${it.message}" }
                        pidOutcome = runCatching { message.credsPid.toString() }
                            .getOrElse { "THREW: ${it.message}" }
                        token
                    }
                }
            }
            val proxy = createProxy(client, EMPTY_DESTINATION, path, runEventLoopThread = false)
            try {
                proxy.callMethod<Int>(iface, member) { call(7) }
            } finally {
                registration.release()
                proxy.release()
                obj.release()
                serverConnection.stopEventLoop()
                client.stopEventLoop()
                client.release()
                serverConnection.release()
            }
        }
        context.close()
        unlink(socketPath)

        println("PROBE199 process uid=${getuid()} pid=${getpid()}")
        println("PROBE199 incoming direct-connection message sender=$observedSender")
        println("PROBE199 credsUid=$uidOutcome")
        println("PROBE199 credsPid=$pidOutcome")
    }

    private fun openUnixSocket(socketPath: String): Int = memScoped {
        val sock = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
        require(sock >= 0) { "Create socket failed" }
        val sa = cValue<sockaddr_un>().getPointer(this)
        memset(sa, 0, sizeOf<sockaddr_un>().convert())
        sa[0].sun_family = AF_UNIX.convert()
        val size = sizeOf<sockaddr_un>() - sizeOf<sa_family_tVar>()
        snprintf(sa[0].sun_path, size.convert(), "%s", socketPath.cstr)
        unlink(socketPath)
        umask(0u)
        require(bind(sock, sa.reinterpret(), sizeOf<sockaddr_un>().convert()) >= 0) { "Bind failed" }
        require(listen(sock, 5) >= 0) { "Listen failed" }
        return sock
    }
}
