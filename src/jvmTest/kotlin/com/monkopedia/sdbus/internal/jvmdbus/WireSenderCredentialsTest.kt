package com.monkopedia.sdbus.internal.jvmdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the effective-id source the JVM backend attaches to a local sender's
 * credentials (issue #247). The defect this pins is invisible on an ordinary process, where the
 * real and effective ids are equal — so these cases feed `/proc/<pid>/status` text in which they
 * DIFFER, which is the only way to see which column was read.
 */
class WireSenderCredentialsTest {

    // A real /proc/self/status prologue, trimmed to the lines that matter, with the real id
    // deliberately different from the effective one.
    private val status = listOf(
        "Name:\tjava",
        "Pid:\t4711",
        "Uid:\t1000\t4242\t4242\t1000",
        "Gid:\t1000\t4343\t4343\t1000",
        "Groups:\t10 1000"
    ).joinToString("\n")

    @Test
    fun readsTheEffectiveColumnNotTheRealOne() {
        assertEquals(
            4242u,
            effectiveIdFromProcStatus(status, "Uid:"),
            "Uid: line is real/effective/saved/fs, so the effective uid is the SECOND value " +
                "(4242); reading the first (1000) reports the real uid as the effective one"
        )
        assertEquals(
            4343u,
            effectiveIdFromProcStatus(status, "Gid:"),
            "Gid: line is real/effective/saved/fs, so the effective gid is the SECOND value " +
                "(4343); reading the first (1000) reports the real gid as the effective one"
        )
    }

    @Test
    fun unparseableStatusYieldsNoIdRatherThanAWrongOne() {
        assertNull(effectiveIdFromProcStatus("", "Uid:"))
        assertNull(effectiveIdFromProcStatus("Uid:\t1000\n", "Uid:"), "no effective column")
        assertNull(effectiveIdFromProcStatus("Uid:\t1000\tnobody\n", "Uid:"), "not a number")
    }
}
