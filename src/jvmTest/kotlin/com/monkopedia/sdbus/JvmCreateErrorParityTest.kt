package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the JVM [createError] errno mapping to the output of the native backend, where
 * `sd_bus_error_set_errno` maps errnos to real D-Bus error names and formats the message as
 * `"$customMsg (<strerror text>)"`. The expectations below are the exact name/message pairs
 * the native createError produces for the same inputs (issue #56 / #40).
 */
class JvmCreateErrorParityTest {

    private fun assertError(errNo: Int, expectedName: String, expectedMessage: String) {
        val error = createError(errNo, "Test failed")
        assertEquals(expectedName, error.name, "name for errno $errNo")
        assertEquals(expectedMessage, error.errorMessage, "message for errno $errNo")
    }

    @Test
    fun einval_mapsToInvalidArgs() = assertError(
        22,
        "org.freedesktop.DBus.Error.InvalidArgs",
        "Test failed (Invalid argument)"
    )

    @Test
    fun eacces_mapsToAccessDenied() = assertError(
        13,
        "org.freedesktop.DBus.Error.AccessDenied",
        "Test failed (Permission denied)"
    )

    @Test
    fun eperm_mapsToAccessDenied() = assertError(
        1,
        "org.freedesktop.DBus.Error.AccessDenied",
        "Test failed (Operation not permitted)"
    )

    @Test
    fun enomem_mapsToNoMemory() = assertError(
        12,
        "org.freedesktop.DBus.Error.NoMemory",
        "Test failed (Cannot allocate memory)"
    )

    @Test
    fun etimedout_mapsToTimeout() = assertError(
        110,
        "org.freedesktop.DBus.Error.Timeout",
        "Test failed (Connection timed out)"
    )

    @Test
    fun enoent_mapsToFileNotFound() = assertError(
        2,
        "org.freedesktop.DBus.Error.FileNotFound",
        "Test failed (No such file or directory)"
    )

    @Test
    fun enxio_fallsBackToSystemErrorName() = assertError(
        6,
        "System.Error.ENXIO",
        "Test failed (No such device or address)"
    )

    @Test
    fun ebusy_fallsBackToSystemErrorName() = assertError(
        16,
        "System.Error.EBUSY",
        "Test failed (Device or resource busy)"
    )

    @Test
    fun enodata_fallsBackToSystemErrorName() = assertError(
        61,
        "System.Error.ENODATA",
        "Test failed (No data available)"
    )

    @Test
    fun enotconn_fallsBackToSystemErrorName() = assertError(
        107,
        "System.Error.ENOTCONN",
        "Test failed (Transport endpoint is not connected)"
    )

    @Test
    fun econnreset_mapsToDisconnected() = assertError(
        104,
        "org.freedesktop.DBus.Error.Disconnected",
        "Test failed (Connection reset by peer)"
    )

    // sd_bus_error_set_errno takes the absolute value, so -1 behaves as EPERM. This is the
    // errno most internal JVM-backend failures are raised with.
    @Test
    fun minusOne_behavesAsEperm() = assertError(
        -1,
        "org.freedesktop.DBus.Error.AccessDenied",
        "Test failed (Operation not permitted)"
    )

    @Test
    fun negatedErrno_behavesAsPositive() = assertError(
        -22,
        "org.freedesktop.DBus.Error.InvalidArgs",
        "Test failed (Invalid argument)"
    )

    @Test
    fun unknownErrno_mapsToFailed() = assertError(
        4095,
        "org.freedesktop.DBus.Error.Failed",
        "Test failed (Unknown error 4095)"
    )
}
