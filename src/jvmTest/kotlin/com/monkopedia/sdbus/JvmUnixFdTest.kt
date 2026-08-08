package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertNotEquals
import org.junit.Assume.assumeTrue

class JvmUnixFdTest {
    @Test
    fun constructorFromFd_duplicatesDescriptorWhenSupported() {
        // junixsocket's native support is genuinely optional (it ships binaries per platform), so
        // record a real skip rather than an early return JUnit would report as a pass (#227).
        assumeTrue(
            "junixsocket's native fd support is unavailable on this platform, so descriptor " +
                "duplication cannot be exercised.",
            JvmUnixFdSupport.supportsFdDuplicationSemantics
        )

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
