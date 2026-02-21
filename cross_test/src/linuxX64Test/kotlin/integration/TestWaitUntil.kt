package com.monkopedia.sdbus.integration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.AtomicBoolean
import platform.posix.usleep

inline fun waitUntil(condition: () -> Boolean, timeout: Duration = 5.seconds): Boolean {
    var elapsed = Duration.ZERO
    val step = 5.milliseconds
    while (!condition()) {
        usleep(step.inWholeMicroseconds.toUInt())
        elapsed += step
        if (elapsed > timeout) return false
    }
    return true
}

inline fun waitUntil(flag: AtomicBoolean, timeout: Duration = 5.seconds): Boolean = waitUntil({
    flag.value
}, timeout)
