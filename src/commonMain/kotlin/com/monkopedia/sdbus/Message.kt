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

/********************************************/
/**
 * Message represents a D-Bus message, which can be either method [MethodCall],
 * [MethodReply], [Signal], or a [PlainMessage].
 *
 * Serialization and deserialization functions are provided for types supported
 * by D-Bus.
 *
 * You mostly don't need to work with this class directly if you use high-level
 * APIs of [Object] and [Proxy].
 *
 ***********************************************/
expect sealed class Message {

    internal fun append(item: Boolean)

    internal fun append(item: Short)

    internal fun append(item: Int)

    internal fun append(item: Long)

    internal fun append(item: UByte)

    internal fun append(item: UShort)

    internal fun append(item: UInt)

    internal fun append(item: ULong)

    internal fun append(item: Double)

    internal fun append(item: String)

    internal fun appendObjectPath(item: String)

    internal fun append(item: ObjectPath)

    internal fun append(item: Signature)

    internal fun appendSignature(item: String)

    internal fun append(item: UnixFd)

    internal fun readBoolean(): Boolean

    internal fun readShort(): Short

    internal fun readInt(): Int

    internal fun readLong(): Long

    internal fun readUByte(): UByte

    internal fun readUShort(): UShort

    internal fun readUInt(): UInt

    internal fun readULong(): ULong

    internal fun readDouble(): Double

    internal fun readString(): String

    internal fun readObjectPath(): ObjectPath

    internal fun readSignature(): Signature

    internal fun readUnixFd(): UnixFd

    internal fun readVariant(): Variant

    internal fun openContainer(signature: String)

    internal fun closeContainer()

    internal fun openDictEntry(signature: String)

    internal fun closeDictEntry()

    internal fun openVariant(signature: String)

    internal fun closeVariant()

    internal fun openStruct(signature: String)

    internal fun closeStruct()

    internal fun enterContainer(signature: String)

    internal fun exitContainer()

    internal fun enterDictEntry(signature: String)

    internal fun exitDictEntry()

    internal fun enterVariant(signature: String)

    internal fun exitVariant()

    internal fun enterStruct(signature: String)

    internal fun exitStruct()

    internal operator fun invoke(): Boolean
    internal fun clearFlags()

    /** The interface name this message targets, or `null` if not set. */
    val interfaceName: InterfaceName?

    /** The member (method/signal) name this message targets, or `null` if not set. */
    val memberName: MemberName?

    /** The bus name of the message sender, or `null` if not available. */
    val sender: BusName?

    /** The object path this message targets, or `null` if not set. */
    val objectPath: ObjectPath?

    /** The destination bus name of the message, or `null` if not set. */
    val destination: BusName?

    /**
     * Peeks at the type of the value at the current read position without consuming it.
     *
     * @return The D-Bus type code and, for containers, the contained signature
     */
    fun peekType(): PeekedType

    /**
     * Whether this message wraps a valid underlying D-Bus message.
     *
     * **Backend-dependent — do not branch on this from common code.** On native it is exactly
     * "wraps a live `sd_bus_message`", which is `true` for every message this library hands out,
     * locally built ones included. On the JVM backend it reports an internal flag that only the
     * paths which build a message from the wire or from a dispatch result set: a message from
     * [createPlainMessage] reads `false` there, and [MethodCall.createErrorReply] sets the flag
     * `false` deliberately to mean "this reply carries an error" — a different concept from the
     * one this property is named for, sharing the same field. So `if (!isValid) return` discards
     * locally built messages on JVM and keeps them on native. Which of the two definitions this
     * property should have is unsettled — see
     * [issue #256](https://github.com/Monkopedia/sdbus-kotlin/issues/256) — so treat the value as
     * unspecified for anything but native's `msg != null`.
     */
    val isValid: Boolean

    /** Whether this message carries no body data. */
    val isEmpty: Boolean

    /**
     * Whether the read cursor has reached the end of the message body.
     *
     * **`complete = false` does not stop at the end of the current container on JVM**, inside a
     * variant included: that backend answers whether the cursor has consumed the flat body, so
     * with any body value left after the container it reports `false` where native reports `true`.
     * `complete = true` agrees on both.
     *
     * @param complete When `true`, also requires that any open containers have been exited
     */
    fun isAtEnd(complete: Boolean): Boolean

    /**
     * Copies body values out of this message into [destination], starting at this message's read
     * cursor and consuming what it copies.
     *
     * Only body data moves: [destination] keeps its own header — interface, member, path, sender,
     * destination and sender credentials — so this copies contents, not identity.
     *
     * @param destination Message to copy into
     * @param complete When `true`, copy every value remaining in the currently open container;
     *   when `false`, copy exactly one complete value (a basic value, or a whole container).
     *   (This corrects the parameter description published up to and including 1.0.1, which said
     *   `true` copies "the whole message" and `false` copies "from the current cursor"; both
     *   branches copy from the cursor, and neither backend ever rewound first.)
     */
    fun copyTo(destination: Message, complete: Boolean)

    /** Seals the message, finalizing its body so it can be sent or read back. */
    fun seal()

    /**
     * Resets the read cursor to the start of the message, or of the currently open container.
     *
     * A variant is the only container the JVM backend keeps on its container stack — arrays,
     * structs and dict entries are flat in its payload model — and the cursor inside an entered
     * variant always addresses that variant's single value, so a non-complete rewind there has
     * nothing to move.
     *
     * @param complete When `true`, rewind past all containers to the very beginning; when `false`,
     *   rewind only the currently open container and leave it open. If no container is open the
     *   two are equivalent.
     */
    fun rewind(complete: Boolean)

    /**
     * The PID of the sending process.
     *
     * **This accessor throws rather than returning a fallback.** Credentials are not attached to
     * every message, and when they are missing the read fails with [SdbusException] instead of
     * yielding a null or a sentinel. Availability is not a guaranteed property of a message: it
     * depends on the backend and on the transport the message arrived over, so a caller that can
     * see messages without credentials has to guard the read.
     *
     * **Credentials are only as trustworthy as whatever attached them.** On a brokered bus the
     * daemon stamps the `sender` field authoritatively, so credentials resolved through it
     * describe the peer it names. A [direct connection][createDirectBusConnection] is brokerless:
     * nothing stamps `sender`, the single peer on the other end supplies it, and anything derived
     * from it is peer-asserted rather than verified. Credentials read over that transport are
     * therefore not authoritative and must not be used to make an authorization decision. What
     * they report there is currently backend-dependent and is an open question — see
     * [issue #199](https://github.com/Monkopedia/sdbus-kotlin/issues/199) — so treat it as
     * unspecified rather than as a contract.
     *
     * **On the JVM backend no credential ever describes another process.** Credentials are
     * attached on exactly one path there — a received signal whose sender resolves to another
     * connection inside this same JVM — and the values attached are read from the *receiving*
     * process (`ProcessHandle.current()`, `com.sun.security.auth.module.UnixSystem` and
     * `/proc/self`), which on that path is the same process. Every other message, including any
     * signal or method call that came from a different process, carries no credentials at all, so
     * these accessors throw.
     * Resolving an external peer would need a `GetConnectionCredentials` call the backend does not
     * make. There is therefore no JVM configuration in which these properties report a remote
     * principal: they either throw or describe you. The native backend queries real per-sender
     * credentials for any peer and any message type. Do not write a portable authorization check
     * on these properties — on JVM a comparison against the local uid succeeds because both sides
     * of it came from the same process, not because the sender was authorized.
     *
     * @throws SdbusException if credentials are unavailable for this message
     */
    val credsPid: Int

    /**
     * The real UID of the sender. Carries the same availability and trust caveats as [credsPid],
     * including throwing [SdbusException] when credentials are unavailable.
     */
    val credsUid: UInt

    /**
     * The effective UID of the sender. Carries the same availability and trust caveats as
     * [credsPid], including throwing [SdbusException] when credentials are unavailable.
     *
     * This is a genuine effective UID on both backends, and is never a copy of [credsUid]: native
     * asks sd-bus for `SD_BUS_CREDS_EUID`, and the JVM backend — where the JDK exposes only
     * `getuid()` — reads the effective column of `/proc/self/status`. Where that file cannot be
     * read the JVM backend reports no effective UID and this accessor throws, rather than
     * answering with the real UID.
     */
    val credsEuid: UInt

    /**
     * The real GID of the sender. Carries the same availability and trust caveats as [credsPid],
     * including throwing [SdbusException] when credentials are unavailable.
     */
    val credsGid: UInt

    /**
     * The effective GID of the sender. Carries the same availability and trust caveats as
     * [credsPid], including throwing [SdbusException] when credentials are unavailable.
     *
     * As with [credsEuid] this is a genuine effective GID on both backends and is never a copy of
     * [credsGid]: `SD_BUS_CREDS_EGID` on native, the effective column of `/proc/self/status` on
     * the JVM, and a throw rather than the real GID when that is unreadable.
     */
    val credsEgid: UInt

    /**
     * The supplementary GIDs of the sender. Carries the same availability and trust caveats as
     * [credsPid], including throwing [SdbusException] when credentials are unavailable.
     */
    val credsSupplementaryGids: List<UInt>

    /**
     * The SELinux security context of the sender. Carries the same availability and trust caveats
     * as [credsPid], including throwing [SdbusException] when it is unavailable.
     */
    val seLinuxContext: String
}

/**
 * Result of [Message.peekType]: the D-Bus type code at the current read position and,
 * for container types, the signature of the contained elements.
 */
class PeekedType(
    /**
     * The D-Bus type code of the value at the read position, or `null` if at the end.
     *
     * **`null` is overloaded on the JVM backend.** There it also means "the value at the cursor is
     * Kotlin `null`" and "the value at the cursor is an in-process object whose D-Bus signature
     * cannot be inferred" — the latter being a path the JVM backend deliberately supports for
     * same-process traffic. `type == null` is therefore a sound end-of-message test on native
     * (where it maps to sd-bus's end-of-container return) but not on JVM, where it can report the
     * end mid-body and silently truncate a read.
     */
    val type: Char?,
    /** For container types, the signature of the contained elements, otherwise `null`. */
    val contents: String?
) {
    override fun toString(): String = "PeekedType(type=$type, contents=$contents)"
}
