package com.monkopedia.sdbus

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

private inline fun debugPrint(msg: () -> String) {
    if (false) println(msg())
}

/********************************************/
/**
 * @class Message
 *
 * Message represents a D-Bus message, which can be either method call message,
 * method reply message, signal message, or a plain message.
 *
 * Serialization and deserialization functions are provided for types supported
 * by D-Bus.
 *
 * You mostly don't need to work with this class directly if you use high-level
 * APIs of @c IObject and @c IProxy.
 *
 ***********************************************/
expect open class Message {

    internal fun append(item: Boolean): Unit

    internal fun append(item: Short): Unit

    internal fun append(item: Int): Unit

    internal fun append(item: Long): Unit

    internal fun append(item: UByte): Unit

    internal fun append(item: UShort): Unit

    internal fun append(item: UInt): Unit

    internal fun append(item: ULong): Unit

    internal fun append(item: Double): Unit

    internal fun append(item: String)

    internal fun appendObjectPath(item: String)

    internal fun append(item: ObjectPath)

    internal fun append(item: Signature)

    internal fun appendSignature(item: String)

    internal fun append(item: UnixFd): Unit

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

inline fun <reified T : Any> Message.serialize(arg: T) {
    serialize(serializer<T>(), serializersModuleOf(serializer<T>()), arg)
}

expect fun <T> Message.serialize(
    serializer: SerializationStrategy<T>,
    module: SerializersModule,
    arg: T
)

inline fun <reified T : Any> Message.deserialize(): T =
    deserialize(serializer<T>(), serializersModuleOf(serializer<T>()))

expect fun <T : Any> Message.deserialize(
    serializer: DeserializationStrategy<T>,
    module: SerializersModule
): T

internal fun Message.append(variant: Variant) {
    variant.serializeTo(this)
}

internal expect inline fun <T> Message.deserializeArrayFast(
    signature: SdbusSig,
    items: MutableList<T>
)

internal fun TypedArguments.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST")
    val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.serialize(
    types: List<KSerializer<*>>,
    args: List<Any>,
    module: SerializersModule
) {
    for ((s, a) in types.zip(args)) {
        @Suppress("UNCHECKED_CAST")
        serialize(s as KSerializer<Any>, module, a)
    }
}

fun Message.serialize(typedArgs: TypedArguments) {
    val types = typedArgs.inputType.map { it.type }
    val args = typedArgs.values
    val module = typedArgs.module()
    serialize(types, args, module)
}

internal fun TypedMethod.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST")
    val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.deserialize(
    types: List<KSerializer<*>>,
    module: SerializersModule
): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return types.map { deserialize(it as KSerializer<Any>, module) }
}

internal fun Message.deserialize(typedArgs: TypedMethod): List<Any> {
    val types = typedArgs.inputType.map { it.type }
    val module = typedArgs.module()
    return deserialize(types, module)
}

internal fun <T : Any> Message.serialize(outputType: Typed<T>, result: T) {
    val module = serializersModuleOf(outputType.cls, outputType.type)
    serialize(outputType.type, module, result)
}
