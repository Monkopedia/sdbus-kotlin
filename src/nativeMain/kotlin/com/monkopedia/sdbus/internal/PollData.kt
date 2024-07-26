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
