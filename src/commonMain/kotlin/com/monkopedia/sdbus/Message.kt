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

    fun getInterfaceName(): String?

    fun getMemberName(): String?

    fun getSender(): String?

    fun getPath(): String?

    fun getDestination(): String?

    fun peekType(): Pair<Char?, String?>

    val isValid: Boolean
    val isEmpty: Boolean
    fun isAtEnd(complete: Boolean): Boolean

    fun copyTo(destination: Message, complete: Boolean)

    fun seal()

    fun rewind(complete: Boolean)

    fun getCredsPid(): Int

    fun getCredsUid(): UInt

    fun getCredsEuid(): UInt

    fun getCredsGid(): UInt

    fun getCredsEgid(): UInt

    fun getCredsSupplementaryGids(): List<UInt>

    fun getSELinuxContext(): String
}
