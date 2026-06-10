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

    /** Returns the interface name this message targets, or `null` if not set. */
    fun getInterfaceName(): String?

    /** Returns the member (method/signal) name this message targets, or `null` if not set. */
    fun getMemberName(): String?

    /** Returns the bus name of the message sender, or `null` if not available. */
    fun getSender(): String?

    /** Returns the object path this message targets, or `null` if not set. */
    fun getPath(): String?

    /** Returns the destination bus name of the message, or `null` if not set. */
    fun getDestination(): String?

    /**
     * Peeks at the type of the value at the current read position without consuming it.
     *
     * @return A pair of the D-Bus type code and, for containers, the contained signature
     */
    fun peekType(): Pair<Char?, String?>

    /** Whether this message wraps a valid underlying D-Bus message. */
    val isValid: Boolean

    /** Whether this message carries no body data. */
    val isEmpty: Boolean

    /**
     * Whether the read cursor has reached the end of the message body.
     *
     * @param complete When `true`, also requires that any open containers have been exited
     */
    fun isAtEnd(complete: Boolean): Boolean

    /**
     * Copies the contents of this message into [destination].
     *
     * @param destination Message to copy into
     * @param complete When `true`, copy the whole message; otherwise copy from the current cursor
     */
    fun copyTo(destination: Message, complete: Boolean)

    /** Seals the message, finalizing its body so it can be sent or read back. */
    fun seal()

    /**
     * Resets the read cursor to the start of the message.
     *
     * @param complete When `true`, rewind past all containers to the very beginning
     */
    fun rewind(complete: Boolean)

    /** Returns the PID of the sending process, if credentials are available. */
    fun getCredsPid(): Int

    /** Returns the real UID of the sender, if credentials are available. */
    fun getCredsUid(): UInt

    /** Returns the effective UID of the sender, if credentials are available. */
    fun getCredsEuid(): UInt

    /** Returns the real GID of the sender, if credentials are available. */
    fun getCredsGid(): UInt

    /** Returns the effective GID of the sender, if credentials are available. */
    fun getCredsEgid(): UInt

    /** Returns the supplementary GIDs of the sender, if credentials are available. */
    fun getCredsSupplementaryGids(): List<UInt>

    /** Returns the SELinux security context of the sender. */
    fun getSELinuxContext(): String
}
