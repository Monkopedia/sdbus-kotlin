/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.internal.PollData
import com.monkopedia.sdbus.internal.absoluteTimeoutOf
import com.monkopedia.sdbus.internal.now
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.UINT64_MAX

class PollDataTest {

    @Test
    fun `PollData ReturnsZeroRelativeTimeoutForZeroAbsoluteTimeout`() {
        val pd = PollData(timeout = 0.microseconds)

        val relativeTimeout = pd.getRelativeTimeout()

        assertEquals(0.microseconds, relativeTimeout)
    }

    @Test
    fun `PollData ReturnsZeroPollTimeoutForZeroAbsoluteTimeout`() {
        val pd = PollData(timeout = 0.microseconds)

        val pollTimeout = pd.getPollTimeout()

        assertEquals(0, pollTimeout)
    }

    @Test
    fun `PollData ReturnsInfiniteRelativeTimeoutForInfiniteAbsoluteTimeout`() {
        val pd = PollData(timeout = Duration.INFINITE)

        val relativeTimeout = pd.getRelativeTimeout()

        assertEquals(Duration.INFINITE, relativeTimeout)
    }

    @Test
    fun `PollData ReturnsNegativePollTimeoutForInfiniteAbsoluteTimeout`() {
        val pd = PollData(timeout = Duration.INFINITE)

        val pollTimeout = pd.getPollTimeout()

        assertEquals(-1, pollTimeout)
    }

    @Test
    fun `PollData ReturnsZeroRelativeTimeoutForPastAbsoluteTimeout`() {
        val past = now() - 10.seconds
        val pd = PollData(timeout = past)

        val relativeTimeout = pd.getRelativeTimeout()

        assertEquals(0.microseconds, relativeTimeout)
    }

    @Test
    fun `PollData ReturnsZeroPollTimeoutForPastAbsoluteTimeout`() {
        val past = now() - 10.seconds
        val pd = PollData(timeout = past)

        val pollTimeout = pd.getPollTimeout()

        assertEquals(0, pollTimeout)
    }

    @Test
    fun `PollData ReturnsCorrectRelativeTimeoutForFutureAbsoluteTimeout`() {
        val future = now() + 1.seconds
        val pd = PollData(timeout = future)

        val relativeTimeout = pd.getRelativeTimeout()

        assertTrue(relativeTimeout in 900.milliseconds..1100.milliseconds)
    }

    @Test
    fun `PollData ReturnsCorrectPollTimeoutForFutureAbsoluteTimeout`() {
        val future = now() + 1.seconds
        val pd = PollData(timeout = future)

        val pollTimeout = pd.getPollTimeout()

        assertTrue(pollTimeout in 900..1100)
    }

    @Test
    fun `PollData ConvertsUint64MaxSentinelToInfiniteTimeout`() {
        assertEquals(Duration.INFINITE, absoluteTimeoutOf(UINT64_MAX))
    }

    @Test
    fun `PollData ConvertsDefaultTimeoutDeadlineToInfiniteTimeout`() {
        // A call left at the default timeout sends sd-bus Long.MAX_VALUE microseconds, so sd-bus
        // stores an absolute deadline of now + Long.MAX_VALUE. That has bit 63 set but is not
        // UINT64_MAX.
        val deadline = now().inWholeMicroseconds.toULong() + Long.MAX_VALUE.toULong()

        assertEquals(Duration.INFINITE, absoluteTimeoutOf(deadline))
    }

    @Test
    fun `PollData ReturnsNegativePollTimeoutForDefaultTimeoutDeadline`() {
        // The whole point: poll(2) must block rather than return immediately, or the event loop
        // busy-spins for the entire duration of the call.
        val deadline = now().inWholeMicroseconds.toULong() + Long.MAX_VALUE.toULong()
        val pd = PollData(timeout = absoluteTimeoutOf(deadline))

        assertEquals(-1, pd.getPollTimeout())
    }

    @Test
    fun `PollData ConvertsRepresentableDeadlinesExactly`() {
        assertEquals(1_500_000.microseconds, absoluteTimeoutOf(1_500_000uL))
        // The largest deadline that still fits in a signed 64-bit integer stays finite.
        assertEquals(Long.MAX_VALUE.microseconds, absoluteTimeoutOf(Long.MAX_VALUE.toULong()))
    }
}
