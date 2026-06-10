package com.monkopedia.sdbus

private const val DBUS_ERROR_PREFIX = "org.freedesktop.DBus.Error."

private class ErrnoMapping(val name: String, val description: String)

// errno -> (D-Bus error name, strerror text), mirroring sd-bus's sd_bus_error_set_errno
// (bus_error_name_from_errno + strerror) so that the JVM backend produces the same error
// names and message suffixes as the native backend for the same errno values.
private val errnoMappings: Map<Int, ErrnoMapping> = mapOf(
    // EPERM
    1 to ErrnoMapping("${DBUS_ERROR_PREFIX}AccessDenied", "Operation not permitted"),
    // ENOENT
    2 to ErrnoMapping("${DBUS_ERROR_PREFIX}FileNotFound", "No such file or directory"),
    // ESRCH
    3 to ErrnoMapping("${DBUS_ERROR_PREFIX}UnixProcessIdUnknown", "No such process"),
    // EIO
    5 to ErrnoMapping("${DBUS_ERROR_PREFIX}IOError", "Input/output error"),
    // ENXIO
    6 to ErrnoMapping("System.Error.ENXIO", "No such device or address"),
    // ENOMEM
    12 to ErrnoMapping("${DBUS_ERROR_PREFIX}NoMemory", "Cannot allocate memory"),
    // EACCES
    13 to ErrnoMapping("${DBUS_ERROR_PREFIX}AccessDenied", "Permission denied"),
    // EBUSY
    16 to ErrnoMapping("System.Error.EBUSY", "Device or resource busy"),
    // EEXIST
    17 to ErrnoMapping("${DBUS_ERROR_PREFIX}FileExists", "File exists"),
    // EINVAL
    22 to ErrnoMapping("${DBUS_ERROR_PREFIX}InvalidArgs", "Invalid argument"),
    // EBADR
    53 to ErrnoMapping("System.Error.EBADR", "Invalid request descriptor"),
    // ENODATA
    61 to ErrnoMapping("System.Error.ENODATA", "No data available"),
    // EBADMSG
    74 to ErrnoMapping("${DBUS_ERROR_PREFIX}InconsistentMessage", "Bad message"),
    // EOPNOTSUPP
    95 to ErrnoMapping("${DBUS_ERROR_PREFIX}NotSupported", "Operation not supported"),
    // EADDRINUSE
    98 to ErrnoMapping("${DBUS_ERROR_PREFIX}AddressInUse", "Address already in use"),
    // ECONNRESET
    104 to ErrnoMapping("${DBUS_ERROR_PREFIX}Disconnected", "Connection reset by peer"),
    // ENOBUFS
    105 to ErrnoMapping("${DBUS_ERROR_PREFIX}LimitsExceeded", "No buffer space available"),
    // ENOTCONN
    107 to ErrnoMapping("System.Error.ENOTCONN", "Transport endpoint is not connected"),
    // ETIMEDOUT
    110 to ErrnoMapping("${DBUS_ERROR_PREFIX}Timeout", "Connection timed out"),
    // EHOSTUNREACH
    113 to ErrnoMapping("System.Error.EHOSTUNREACH", "No route to host")
)

/**
 * Mirrors the native [createError] (Error.native.kt), which delegates to sd-bus's
 * `sd_bus_error_set_errno`: well-known errnos map to real D-Bus error names (e.g.
 * EINVAL -> org.freedesktop.DBus.Error.InvalidArgs), unmapped-but-named errnos to
 * `System.Error.<ERRNO_NAME>`, unknown values to org.freedesktop.DBus.Error.Failed,
 * and the message is `"$customMsg (<strerror text>)"`. Keeping the same mapping on
 * the JVM makes error names a cross-backend contract instead of a backend detail.
 */
internal actual fun createError(errNo: Int, customMsg: String): Error {
    // sd_bus_error_set_errno accepts negated errnos (e.g. -EINVAL) and takes the absolute value.
    val errno = if (errNo < 0) -errNo else errNo
    if (errno == 0) {
        // sd_bus_error_set_errno(0) leaves the error unset; the native createError then falls
        // back to "$errNo" as the name and an empty strerror text.
        return Error("$errNo", "$customMsg ()")
    }
    val mapping = errnoMappings[errno]
        ?: ErrnoMapping("${DBUS_ERROR_PREFIX}Failed", "Unknown error $errno")
    return Error(mapping.name, "$customMsg (${mapping.description})")
}
