package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the sender credentials the JVM backend attaches to a message from one of its
 * own connections (issue #247), in both halves: which column of `/proc/<pid>/status` the effective
 * ids are read from, and which field each id is then written to.
 *
 * Every case here supplies ids that DIFFER from one another. That is the point: on any real
 * process the effective ids equal the real ones, so a case built from this process's own identity
 * cannot see the difference between reading the effective id and copying the real one, and would
 * stay green with #247's substitution restored.
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

    @Test
    fun eachIdIsWrittenToItsOwnField() {
        val sender = ":1.247-${System.nanoTime()}"
        val creds = WireSenderCredentials(
            pid = 11,
            uid = 22u,
            euid = 33u,
            gid = 44u,
            egid = 55u,
            supplementaryGids = listOf(66u),
            selinuxContext = "unconfined_u:unconfined_r:unconfined_t"
        )
        LocalJvmServiceRegistry.registerLocalUniqueName(sender)
        val stamped = try {
            Message.Metadata(sender = sender).withLocalSenderCredentials(sender, creds)
        } finally {
            LocalJvmServiceRegistry.unregisterLocalUniqueName(sender)
        }

        assertEquals(11, stamped.credsPid)
        assertEquals(22u, stamped.credsUid)
        assertEquals(33u, stamped.credsEuid, "credsEuid must carry the euid, not the uid (22)")
        assertEquals(44u, stamped.credsGid)
        assertEquals(55u, stamped.credsEgid, "credsEgid must carry the egid, not the gid (44)")
        assertEquals(listOf(66u), stamped.credsSupplementaryGids)
        assertEquals(creds.selinuxContext, stamped.selinuxContext)
    }

    @Test
    fun aSenderThatIsNotOneOfOurConnectionsGetsNoCredentials() {
        val creds = WireSenderCredentials(1, 2u, 3u, 4u, 5u, listOf(6u), "ctx")
        val stamped = Message.Metadata(sender = ":1.not-ours")
            .withLocalSenderCredentials(":1.not-ours", creds)

        assertNull(stamped.credsUid)
        assertNull(stamped.credsEuid)
        assertNull(stamped.credsEgid)
    }
}
