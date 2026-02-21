package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.JvmUnixFdSupport
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal actual object FdTestSupport {
    private val nextFd = AtomicInteger(1000)
    private val openFds = ConcurrentHashMap.newKeySet<Int>()

    actual val supportsFdDuplicationSemantics: Boolean =
        JvmUnixFdSupport.supportsFdDuplicationSemantics

    actual fun createPipePair(): Pair<Int, Int> {
        if (supportsFdDuplicationSemantics) {
            return checkNotNull(JvmUnixFdSupport.createPipePair()) {
                "Expected native pipe support for JVM UnixFd tests"
            }
        }
        val readFd = nextFd.getAndIncrement()
        val writeFd = nextFd.getAndIncrement()
        openFds += readFd
        openFds += writeFd
        return readFd to writeFd
    }

    actual fun closeFd(fd: Int): Int {
        if (supportsFdDuplicationSemantics) {
            return if (JvmUnixFdSupport.closeFd(fd)) 0 else -1
        }
        return if (openFds.remove(fd)) 0 else -1
    }
}
