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
package com.monkopedia.sdbus

/**
 * Exception type representing a D-Bus or sdbus-kotlin error.
 *
 * Thrown by virtually all sdbus-kotlin operations on failure, including when a remote method
 * returns a D-Bus error reply. The [name] carries the D-Bus error name (e.g.
 * `org.freedesktop.DBus.Error.UnknownMethod`), which callers can match against to handle specific
 * error conditions.
 *
 * The inherited [message] is deterministically derived from the two properties as
 * `"$name: $errorMessage"`; [errorMessage] alone carries the human-readable detail without the
 * D-Bus error name prefix.
 *
 * @property name The D-Bus error name
 * @property errorMessage Human-readable detail describing the error
 */
class SdbusException(val name: String, val errorMessage: String = "") :
    Exception("$name: $errorMessage")

// The generic D-Bus error name for a failure with no more specific name. A non-SdbusException from
// a handler maps to this VALID name so both backends report it identically; the former behavior put
// the exception message in the name slot, producing an invalid name — native then failed to send a
// proper error reply (client saw NoReply) while JVM passed the bogus name through. See #141.
internal const val GENERIC_DBUS_ERROR_NAME = "org.freedesktop.DBus.Error.Failed"

internal fun Throwable.toError(): SdbusException =
    (this as? SdbusException) ?: SdbusException(GENERIC_DBUS_ERROR_NAME, message ?: toString())

/**
 * Creates an [SdbusException] from a numeric error code and a custom message.
 *
 * The error code is mapped to a corresponding D-Bus error name where possible.
 *
 * @param errNo Numeric (errno-style) error code
 * @param customMsg Additional human-readable context for the error
 * @return The constructed [SdbusException]
 */
internal expect fun createError(errNo: Int, customMsg: String): SdbusException

internal inline fun sdbusRequire(condition: () -> Boolean, msg: String, errNo: Int) {
    if (condition()) throw createError((errNo), (msg))
}

internal fun sdbusRequire(condition: Boolean, msg: String, errNo: Int) {
    if (condition) throw createError((errNo), (msg))
}
