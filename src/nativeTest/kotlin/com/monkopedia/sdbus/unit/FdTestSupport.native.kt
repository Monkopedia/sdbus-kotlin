@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.monkopedia.sdbus.unit

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.posix.close
import platform.posix.pipe

internal actual object FdTestSupport {
    actual val supportsFdDuplicationSemantics: Boolean = true

    actual fun createPipePair(): Pair<Int, Int> = memScoped {
        val pair = allocArray<IntVar>(2)
        check(pipe(pair) == 0) { "pipe failed" }
        pair[0] to pair[1]
    }

    actual fun closeFd(fd: Int): Int = close(fd)
}
