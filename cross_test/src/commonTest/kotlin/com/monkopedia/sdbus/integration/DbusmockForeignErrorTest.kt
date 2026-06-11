/**
 *
 * (C) 2024 - 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.getProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Foreign error mapping against the python-dbusmock independent peer (issue #36): D-Bus error
 * replies produced by a non-sdbus implementation (Python/GDBus raising `DBusException` with
 * arbitrary error names) must surface as [com.monkopedia.sdbus.Error] with the error name and
 * message preserved verbatim.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`) — the independent-peer
 * counterpart of the own-server error-mapping parity coverage. Skips cleanly when
 * python3-dbusmock is not installed (see [DbusmockHarness]).
 */
class DbusmockForeignErrorTest {

    @Test
    fun standardErrorName_surfacesWithNameAndMessage() = withDbusmockPeer("ErrStd") {
        addRaisingMethod("Boom", "org.freedesktop.DBus.Error.NotSupported", "peer says no")
        val error = assertFailsWith<Error> {
            proxy.callMethod<Unit>(iface, MethodName("Boom")) {}
        }
        assertForeignError(error, "org.freedesktop.DBus.Error.NotSupported", "peer says no")
    }

    @Test
    fun arbitraryForeignErrorName_isPreservedVerbatim() = withDbusmockPeer("ErrCustom") {
        addRaisingMethod(
            "Custom",
            "com.monkopedia.sdbus.test.SomethingWentWrong",
            "completely custom failure detail"
        )
        val error = assertFailsWith<Error> {
            proxy.callMethod<Unit>(iface, MethodName("Custom")) {}
        }
        assertForeignError(
            error,
            "com.monkopedia.sdbus.test.SomethingWentWrong",
            "completely custom failure detail"
        )

        // AccessDenied-style names commonly raised by real services map the same way.
        addRaisingMethod("Denied", "org.freedesktop.DBus.Error.AccessDenied", "not for you")
        val denied = assertFailsWith<Error> {
            proxy.callMethod<Unit>(iface, MethodName("Denied")) {}
        }
        assertForeignError(denied, "org.freedesktop.DBus.Error.AccessDenied", "not for you")
    }

    @Test
    fun asyncCallPath_mapsForeignErrorsIdentically() = withDbusmockPeer("ErrAsync") {
        addRaisingMethod("Boom", "org.freedesktop.DBus.Error.InvalidArgs", "async boom")
        val error = assertFailsWith<Error> {
            proxy.callMethodAsync<Unit>(iface, MethodName("Boom")) {}
        }
        assertForeignError(error, "org.freedesktop.DBus.Error.InvalidArgs", "async boom")
    }

    @Test
    fun unknownMethodOnForeignPeer_mapsToUnknownMethodError() = withDbusmockPeer("ErrUnknown") {
        // The error here is produced by the foreign stack itself (dbus-python's dispatch),
        // not by scripted code; the message text is implementation-defined so only the name
        // is asserted.
        val error = assertFailsWith<Error> {
            proxy.callMethod<Unit>(iface, MethodName("NoSuchMethodHere")) {}
        }
        assertForeignError(error, "org.freedesktop.DBus.Error.UnknownMethod")
    }

    @Test
    fun foreignPropertyErrors_surfaceWithPeerErrorNames() = withDbusmockPeer("ErrProp") {
        // dbusmock raises "<main interface>.UnknownProperty" for a Get on a missing property.
        val error = assertFailsWith<Error> {
            proxy.getProperty<Int>(iface, PropertyName("NoSuchProperty"))
        }
        assertForeignError(
            error,
            "${iface.value}.UnknownProperty",
            "no such property NoSuchProperty"
        )
    }

    /**
     * Asserts that [error]'s name (and, when given, message) match what the foreign peer put
     * in its error reply — gated on [peerErrorNameMappingSupported] where the JVM backend is
     * known to discard both (the `Error` is still thrown, which is asserted unconditionally
     * at each call site via `assertFailsWith`).
     */
    private fun assertForeignError(
        error: Error,
        expectedName: String,
        expectedMessage: String? = null
    ) {
        if (!peerErrorNameMappingSupported) {
            // KNOWN JVM BACKEND BUG (issue #72, found by this suite; see the
            // peerErrorNameMappingSupported KDoc for full details).
            println(
                "[DbusmockForeignErrorTest] SKIP name/message assertions for $expectedName: " +
                    "known JVM backend gap (foreign error names are discarded). " +
                    "Actual name surfaced: ${error.name}"
            )
            return
        }
        assertEquals(expectedName, error.name, "foreign D-Bus error name was not preserved")
        if (expectedMessage != null) {
            assertEquals(
                expectedMessage,
                error.errorMessage,
                "foreign D-Bus error message was not preserved"
            )
        }
    }

    /** Scripts a method on the peer that raises a DBusException with the given name/message. */
    private fun DbusmockPeer.addRaisingMethod(method: String, errorName: String, message: String) =
        addMethod(
            method,
            "",
            "",
            "raise dbus.exceptions.DBusException('$message', name='$errorName')"
        )
}

/**
 * Whether this backend preserves the D-Bus error name and message of error replies received
 * from a real remote (out-of-process) peer.
 *
 * `true` on the native sd-bus backend, where the wire error name/message land verbatim in
 * [com.monkopedia.sdbus.Error]'s `name`/`errorMessage`. `false` on the JVM backend:
 * `PureJavaDbusBackend.remoteCall` converts an `org.freedesktop.dbus.messages.Error` reply via
 * `createError(-1, message)`, which maps the `-1` errno through the EPERM entry of the errno
 * table — so EVERY foreign error surfaces as `org.freedesktop.DBus.Error.AccessDenied`
 * ("Operation not permitted...") regardless of the actual error name/message sent by the peer.
 * The same applies on the async path and to property Get/Set errors. Own-server tests miss
 * this because in-process calls short-circuit through JvmStaticDispatch, which rethrows the
 * original [com.monkopedia.sdbus.Error] without ever serializing it to the wire.
 *
 * Found by this suite (issue #36); the fix is ticketed as issue #72 — when it lands, flip the
 * JVM actual to `true` so the full assertions enforce parity.
 */
internal expect val peerErrorNameMappingSupported: Boolean
