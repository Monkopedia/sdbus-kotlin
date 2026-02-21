package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.UnixFd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnixFdCommonTest {
    @Test
    fun aUnixFd_CanBeConstructedFromPipeDescriptor() {
        val (readFd, writeFd) = FdTestSupport.createPipePair()
        val unixFd = UnixFd(readFd)
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertTrue(unixFd.fd != readFd)
        } else {
            assertEquals(readFd, unixFd.fd)
        }
        assertEquals(0, FdTestSupport.closeFd(readFd))
        assertEquals(0, FdTestSupport.closeFd(writeFd))
    }

    @Test
    fun aUnixFd_AdoptsFdAsIsWhenRequested() {
        val (readFd, writeFd) = FdTestSupport.createPipePair()
        val unixFd = UnixFd(readFd, adoptFd = Unit)
        assertEquals(readFd, unixFd.fd)
        unixFd.release()
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        } else {
            assertEquals(0, FdTestSupport.closeFd(readFd))
        }
        assertEquals(0, FdTestSupport.closeFd(writeFd))
    }

    @Test
    fun aUnixFd_CanBeCopyConstructed() {
        val (readFd, writeFd) = FdTestSupport.createPipePair()
        val original = UnixFd(readFd, adoptFd = Unit)
        val copy = UnixFd(original)
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertTrue(copy.fd != original.fd)
        } else {
            assertEquals(original.fd, copy.fd)
        }
        original.release()
        copy.release()
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        } else {
            assertEquals(0, FdTestSupport.closeFd(readFd))
        }
        assertEquals(0, FdTestSupport.closeFd(writeFd))
    }

    @Test
    fun aUnixFd_ClosesOwnedDescriptorsAfterRelease() {
        val (readFd, writeFd) = FdTestSupport.createPipePair()
        val original = UnixFd(readFd, adoptFd = Unit)
        val copy = UnixFd(original)
        val originalFd = original.fd
        val copyFd = copy.fd

        original.release()
        copy.release()

        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertTrue(copyFd != originalFd)
            assertEquals(-1, FdTestSupport.closeFd(originalFd))
            assertEquals(-1, FdTestSupport.closeFd(copyFd))
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        } else {
            assertEquals(originalFd, copyFd)
            assertEquals(0, FdTestSupport.closeFd(originalFd))
            assertEquals(-1, FdTestSupport.closeFd(copyFd))
        }
        assertEquals(0, FdTestSupport.closeFd(writeFd))
    }

    @Test
    fun aUnixFd_ReleaseKeepsDescriptorClosable() {
        val (readFd, writeFd) = FdTestSupport.createPipePair()
        val unixFd = UnixFd(readFd, adoptFd = Unit)

        unixFd.release()

        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertEquals(-1, FdTestSupport.closeFd(unixFd.fd))
        } else {
            assertEquals(0, FdTestSupport.closeFd(unixFd.fd))
        }
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        } else {
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        }
        assertEquals(0, FdTestSupport.closeFd(writeFd))
    }

    @Test
    fun aUnixFd_ReleaseIsIdempotent() {
        val (readFd, writeFd) = FdTestSupport.createPipePair()
        val unixFd = UnixFd(readFd, adoptFd = Unit)

        unixFd.release()
        unixFd.release()

        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertEquals(-1, FdTestSupport.closeFd(unixFd.fd))
        } else {
            assertEquals(0, FdTestSupport.closeFd(unixFd.fd))
            assertEquals(-1, FdTestSupport.closeFd(unixFd.fd))
        }
        if (FdTestSupport.supportsFdDuplicationSemantics) {
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        } else {
            assertEquals(-1, FdTestSupport.closeFd(readFd))
        }
        assertEquals(0, FdTestSupport.closeFd(writeFd))
    }
}
