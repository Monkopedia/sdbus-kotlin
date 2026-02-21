package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingAsyncCallJvmTest {
    @Test
    fun release_invokesCancelActionOnlyOnce() {
        var cancelCount = 0
        val pending = PendingAsyncCall(
            cancelAction = { cancelCount += 1 },
            isPendingAction = { true }
        )

        pending.release()
        pending.release()

        assertFalse(pending.isPending())
        assertEquals(1, cancelCount)
    }

    @Test
    fun isPending_reflectsDelegateUntilReleased() {
        var state = true
        val pending = PendingAsyncCall(
            cancelAction = {},
            isPendingAction = { state }
        )

        assertTrue(pending.isPending())
        state = false
        assertFalse(pending.isPending())
        state = true
        pending.release()
        assertFalse(pending.isPending())
    }
}
