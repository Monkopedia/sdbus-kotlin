/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
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
/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus.internal

import kotlin.time.Duration

/**
 * @struct PollData
 *
 * Carries poll data needed for integration with external event loop implementations.
 *
 * See getEventLoopPollData() for more info.
 */
internal data class PollData internal constructor(
    /**
     * The read fd to be monitored by the event loop.
     */
    val fd: Int = 0,

    /**
     * The events to use for poll(2) alongside fd.
     */
    internal val events: Short = 0,

    /**
     * Absolute timeout value in microseconds, based of CLOCK_MONOTONIC.
     *
     * Call getPollTimeout() to get timeout recalculated to relative timeout that can be passed to poll(2).
     */
    val timeout: Duration? = null,

    /**
     * An additional event fd to be monitored by the event loop for POLLIN events.
     */
    val eventFd: Int = 0
) {

    /**
     * Returns the timeout as relative value from now.
     *
     * Returned value is std::chrono::microseconds::max() if the timeout is indefinite.
     *
     * @return Relative timeout as a time duration
     */
    fun getRelativeTimeout(): Duration = when (timeout) {
        null, Duration.ZERO -> Duration.ZERO
        Duration.INFINITE -> Duration.INFINITE
        else -> (timeout - com.monkopedia.sdbus.now()).coerceAtLeast(Duration.ZERO)
    }

    /**
     * Returns relative timeout in the form which can be passed as argument 'timeout' to poll(2)
     *
     * @return -1 if the timeout is indefinite. 0 if the poll(2) shouldn't block.
     *         An integer in milliseconds otherwise.
     */
    fun getPollTimeout(): Int {
        val relativeTimeout = getRelativeTimeout()

        return if (relativeTimeout == Duration.INFINITE) {
            -1
        } else {
            relativeTimeout.inWholeMilliseconds.toInt()
        }
    }
}
