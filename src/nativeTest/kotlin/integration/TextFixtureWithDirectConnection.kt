@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Connection
import com.monkopedia.sdbus.header.IConnection
import com.monkopedia.sdbus.header.createBusConnection
import com.monkopedia.sdbus.header.createDirectBusConnection
import com.monkopedia.sdbus.header.createServerBus
import kotlin.native.concurrent.ThreadLocal
import kotlin.native.internal.NativePtr.Companion.NULL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
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
import platform.posix.sa_family_t
import platform.posix.sa_family_tVar
import platform.posix.snprintf
import platform.posix.socket
import platform.posix.umask
import platform.posix.unlink
import platform.posix.usleep

@ThreadLocal
val leaker = Arena()

@ThreadLocal
val s_adaptorConnection = createBusConnection().own(leaker)

@ThreadLocal
val s_proxyConnection = createBusConnection().own(leaker)


class TextFixtureWithDirectConnection(test: BaseTest) : BaseTestFixture(test) {

    override fun onBeforeTest() {
        super.onBeforeTest()
        val sock = openUnixSocket();
        runBlocking {
            createClientAndServerConnections(sock);
            createAdaptorAndProxyObjects();
        }
    }

    override fun onScopeClosed() {
        runBlocking {
            m_proxyConnection?.leaveEventLoop();
            m_adaptorConnection?.leaveEventLoop();
//            s_adaptorConnection.leaveEventLoop()
//            s_proxyConnection.leaveEventLoop()
            context.close()
        }
    }

    suspend fun CoroutineScope.createClientAndServerConnections(sock: Int) {
        val job = launch(context) {
            try {
                val fd = accept(sock, null, null);
                val set = fcntl(fd, F_SETFD, /*SOCK_NONBLOCK|*/SOCK_CLOEXEC)
                m_adaptorConnection = createServerBus(fd).own(scope)
                // This is necessary so that createDirectBusConnection() below does not block
                m_adaptorConnection?.enterEventLoopAsync();
            } catch (t: Throwable) {
                t.printStackTrace()
                throw t
            }
        }
        try {

        m_proxyConnection =
            createDirectBusConnection("unix:path=$DIRECT_CONNECTION_SOCKET_PATH").own(scope)
        m_proxyConnection?.enterEventLoopAsync();

        job.join();
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

    fun createAdaptorAndProxyObjects() {
        require(m_adaptorConnection != null);
        require(m_proxyConnection != null);

        m_adaptor = TestAdaptor(scope, m_adaptorConnection as Connection, OBJECT_PATH);
        m_adaptor?.registerAdaptor()
        // Destination parameter can be empty in case of direct connections
        m_proxy = TestProxy(scope, m_proxyConnection as Connection, EMPTY_DESTINATION, OBJECT_PATH);
        m_proxy?.registerProxy()
    }

    private val context = newFixedThreadPoolContext(4, "test-context")
    var m_adaptorConnection: IConnection? = null
    var m_proxyConnection: IConnection? = null
    var m_adaptor: TestAdaptor? = null
    var m_proxy: TestProxy? = null

    companion object {
        fun openUnixSocket(): Int = memScoped {
            val sock = socket(AF_UNIX, SOCK_STREAM or SOCK_CLOEXEC, 0);
            require(sock >= 0) {
                "Create socket failed"
            }

            val sa = cValue<sockaddr_un>().getPointer(this)
            memset(sa, 0, sizeOf<sockaddr_un>().convert());
            sa[0].sun_family = AF_UNIX.convert()
            val size = sizeOf<sockaddr_un>() - sizeOf<sa_family_tVar>()
            snprintf(sa[0].sun_path, size.convert(), "%s", DIRECT_CONNECTION_SOCKET_PATH.cstr);


            unlink(DIRECT_CONNECTION_SOCKET_PATH);

            umask(0u);
            var r = bind(sock, sa.reinterpret(), sizeOf<sockaddr_un>().convert())// sa[0].sun_path.toKString().length.convert());
            require(r >= 0) {
                "Bind failed $r"
            }

            r = listen(sock, 5);
            require(r >= 0) {
                "Listen failed $r"
            }


            return sock;
        }
    };
}

inline fun waitUntil(fnc: () -> Boolean, timeout: Duration = 5.seconds): Boolean {
    var elapsed = 0.seconds
    val step = 5.milliseconds
    do {
        usleep(step.inWholeMicroseconds.toUInt());
        elapsed += step;
        if (elapsed > timeout)
            return false
    } while (!fnc());

    return true;
}

inline fun waitUntil(flag: AtomicBoolean, timeout: Duration = 5.seconds): Boolean {
    return waitUntil({ flag.value }, timeout);
}
