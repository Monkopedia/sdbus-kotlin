@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.createDirectBusConnection
import com.monkopedia.sdbus.createServerBus
import kotlin.native.concurrent.ThreadLocal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.coroutines.CoroutineScope
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
import platform.posix.listen
import platform.posix.memset
import platform.posix.sa_family_tVar
import platform.posix.snprintf
import platform.posix.socket
import platform.posix.umask
import platform.posix.unlink
import platform.posix.usleep

@ThreadLocal
val globalAdaptorConnection = com.monkopedia.sdbus.createBusConnection()

@ThreadLocal
val globalProxyConnection = com.monkopedia.sdbus.createBusConnection()

class DirectConnectionFixture(test: BaseTest) : BaseTestFixture(test) {

    override fun onBeforeTest() {
        super.onBeforeTest()
        val sock = openUnixSocket()
        runBlocking {
            createClientAndServerConnections(sock)
            createAdaptorAndProxyObjects()
        }
    }

    override fun onAfterTest() {
        runBlocking {
            proxyConnection?.leaveEventLoop()
            adaptorConnection?.leaveEventLoop()
            context.close()
            proxyConnection = null
            adaptorConnection = null
        }
    }

    private suspend fun CoroutineScope.createClientAndServerConnections(sock: Int) {
        val job = launch(context) {
            val fd = accept(sock, null, null)
            val set = fcntl(
                fd,
                F_SETFD,
                // SOCK_NONBLOCK |
                SOCK_CLOEXEC
            )
            adaptorConnection = createServerBus(fd)
            // This is necessary so that createDirectBusConnection() below does not block
            adaptorConnection?.enterEventLoopAsync()
        }
        proxyConnection = createDirectBusConnection("unix:path=$DIRECT_CONNECTION_SOCKET_PATH")
        proxyConnection?.enterEventLoopAsync()

        job.join()
    }

    fun createAdaptorAndProxyObjects() {
        require(adaptorConnection != null)
        require(proxyConnection != null)

        adaptor = TestAdaptor(adaptorConnection!!, OBJECT_PATH)
        adaptor?.registerAdaptor()
        // Destination parameter can be empty in case of direct connections
        proxy = TestProxy(proxyConnection!!, EMPTY_DESTINATION, OBJECT_PATH)
        proxy?.registerProxy()
    }

    private val context = newFixedThreadPoolContext(4, "test-context")
    var adaptorConnection: com.monkopedia.sdbus.Connection? = null
    var proxyConnection: com.monkopedia.sdbus.Connection? = null
    var adaptor: TestAdaptor? = null
    var proxy: TestProxy? = null

    companion object {
        fun openUnixSocket(): Int = memScoped {
            val sock = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0)
            require(sock >= 0) {
                "Create socket failed"
            }

            val sa = cValue<sockaddr_un>().getPointer(this)
            memset(sa, 0, sizeOf<sockaddr_un>().convert())
            sa[0].sun_family = AF_UNIX.convert()
            val size = sizeOf<sockaddr_un>() - sizeOf<sa_family_tVar>()
            snprintf(sa[0].sun_path, size.convert(), "%s", DIRECT_CONNECTION_SOCKET_PATH.cstr)

            unlink(DIRECT_CONNECTION_SOCKET_PATH)

            umask(0u)
            var r = bind(
                sock,
                sa.reinterpret(),
                sizeOf<sockaddr_un>().convert()
            ) // sa[0].sun_path.toKString().length.convert())
            require(r >= 0) {
                "Bind failed $r"
            }

            r = listen(sock, 5)
            require(r >= 0) {
                "Listen failed $r"
            }

            return sock
        }
    }
}

inline fun waitUntil(fnc: () -> Boolean, timeout: Duration = 5.seconds): Boolean {
    var elapsed = 0.seconds
    val step = 5.milliseconds
    do {
        usleep(step.inWholeMicroseconds.toUInt())
        elapsed += step
        if (elapsed > timeout) {
            return false
        }
    } while (!fnc())

    return true
}

inline fun waitUntil(flag: AtomicBoolean, timeout: Duration = 5.seconds): Boolean = waitUntil({
    flag.value
}, timeout)
