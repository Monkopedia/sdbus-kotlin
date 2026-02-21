package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertNotEquals

class JvmUnixFdTest {
    @Test
    fun constructorFromFd_duplicatesDescriptorWhenSupported() {
        if (!JvmUnixFdSupport.supportsFdDuplicationSemantics) return

        val first = UnixFd(0)
        val second = UnixFd(first.fd)
        try {
            assertNotEquals(first.fd, second.fd, "Expected duplicated descriptor")
        } finally {
            second.release()
            first.release()
        }
    }
}
