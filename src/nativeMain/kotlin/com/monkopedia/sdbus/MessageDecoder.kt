@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.NativeTypeConverter
import kotlinx.cinterop.CVariable
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind.OPEN
import kotlinx.serialization.descriptors.PolymorphicKind.SEALED
import kotlinx.serialization.descriptors.PrimitiveKind.BOOLEAN
import kotlinx.serialization.descriptors.PrimitiveKind.BYTE
import kotlinx.serialization.descriptors.PrimitiveKind.CHAR
import kotlinx.serialization.descriptors.PrimitiveKind.DOUBLE
import kotlinx.serialization.descriptors.PrimitiveKind.FLOAT
import kotlinx.serialization.descriptors.PrimitiveKind.INT
import kotlinx.serialization.descriptors.PrimitiveKind.LONG
import kotlinx.serialization.descriptors.PrimitiveKind.SHORT
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind.CONTEXTUAL
import kotlinx.serialization.descriptors.SerialKind.ENUM
import kotlinx.serialization.descriptors.StructureKind.CLASS
import kotlinx.serialization.descriptors.StructureKind.LIST
import kotlinx.serialization.descriptors.StructureKind.MAP
import kotlinx.serialization.descriptors.StructureKind.OBJECT
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

private val unsignedNames = listOf(
    "UInt",
    "ULong",
    "UByte",
    "UShort"
)

internal class MessageDecoder(
    val target: Message,
    override val serializersModule: SerializersModule
) : BaseMessageDecoder() {

    private val structureStack = SerialDescriptorStack()

    override fun decodeNotNullMark(): Boolean {
        error("Nullables are not allowed for serialization")
    }

    override fun decodeNull(): Nothing? {
        error("Nullables are not allowed for serialization")
    }

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = 0

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when (descriptor.kind) {
            LIST -> {
                val signature = (descriptor.asSignature as ListSig).element
                if (signature.isTrivial && signature != BoolSig) {
                    val converter = (signature as PrimitiveSig<*, *>).converter!!
                    ListDecoder(target, serializersModule, signature, converter)
                } else {
                    structureStack.push(descriptor)
                    target.enterContainer(signature.value)
                    NonSequentialListDecoder(this)
                }
            }

            MAP -> {
                structureStack.push(descriptor)
                val sig = descriptor.asSignature as MapSig
                target.enterContainer(sig.dictValue)
                NonSequentialListDecoder(this)
            }

            CLASS,
            OBJECT -> {
                structureStack.push(descriptor)
                val sig = structureStack.signature as StructSig
                target.enterStruct(sig.contents)
                this
            }

            OPEN,
            SEALED -> error("Polymorphic elements are not allowed for serialization")

            CONTEXTUAL -> error("Contextuals are not allowed for serialization")
            ENUM,
            BOOLEAN,
            BYTE,
            CHAR,
            DOUBLE,
            FLOAT,
            INT,
            LONG,
            SHORT,
            STRING -> error("Primitive types should not have structure")
        }

    internal fun popDescriptor() {
        structureStack.pop(structureStack.peek)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        when (descriptor.kind) {
            LIST, MAP -> target.exitContainer()
            CLASS, OBJECT -> target.exitStruct()
            else -> error("This should be unreachable")
        }
        structureStack.pop(descriptor)
    }

    override fun decodeBoolean(): Boolean = target.readBoolean().also {
        lastInline = null
    }

    override fun decodeByte(): Byte = target.readUByte().toByte().also {
        lastInline = null
    }

    override fun decodeChar(): Char = Char(target.readUByte().toInt()).also {
        lastInline = null
    }

    override fun decodeDouble(): Double = target.readDouble().also {
        lastInline = null
    }

    override fun decodeFloat(): Float = target.readDouble().toFloat().also {
        lastInline = null
    }

    override fun decodeShort(): Short {
        if (isUnsigned) {
            return target.readUShort().toShort()
        } else {
            return target.readShort()
        }
    }

    override fun decodeInt(): Int {
        if (isUnsigned) {
            return target.readUInt().toInt()
        } else {
            return target.readInt()
        }
    }

    override fun decodeLong(): Long {
        if (isUnsigned) {
            return target.readULong().toLong()
        } else {
            return target.readLong()
        }
    }

    override fun decodeString(): String = when (lastInline?.serialName) {
        ObjectPath.SERIAL_NAME -> target.readObjectPath().value
        Signature.SERIAL_NAME -> target.readSignature().value
        else -> target.readString()
    }.also {
        lastInline = null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        if (deserializer.descriptor.serialName == "kotlin.Unit") {
            return Unit as T
        }
        return when (deserializer.descriptor.serialName) {
            UnixFd.SERIAL_NAME -> target.readUnixFd() as T
            Variant.SERIAL_NAME -> target.readVariant() as T
            else -> super.decodeSerializableValue(deserializer)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        if (descriptor == structureStack.head && descriptor.kind == MAP && ((index and 1) == 0)) {
            val signature = structureStack.signature as MapSig
            target.enterDictEntry(signature.contents)
        }
        return when (descriptor.serialName) {
            UnixFd.SERIAL_NAME -> target.readUnixFd() as T
            Variant.SERIAL_NAME -> target.readVariant() as T
            else -> super.decodeSerializableElement(descriptor, index, deserializer, previousValue)
        }.also {
            if (descriptor == structureStack.head &&
                descriptor.kind == MAP &&
                ((index and 1) == 1)
            ) {
                target.exitDictEntry()
            }
        }
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = target.readInt()
}

internal class NonSequentialListDecoder(private val baseDecoder: MessageDecoder) :
    AbstractDecoder() {
    override val serializersModule: SerializersModule
        get() = baseDecoder.serializersModule
    private var index = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (baseDecoder.target.peekType().first == null) {
            return CompositeDecoder.DECODE_DONE
        }
        return index++
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        baseDecoder.target.clearFlags()
        baseDecoder.target.exitContainer()
        baseDecoder.popDescriptor()
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T = baseDecoder.decodeSerializableElement(descriptor, index, deserializer, previousValue)
}

internal class ListDecoder<K, N : CVariable>(
    private val target: Message,
    override val serializersModule: SerializersModule,
    private val signature: SdbusSig,
    private val converter: NativeTypeConverter<K, N>
) : BaseMessageDecoder() {
    private val list = mutableListOf<K>()

    init {
        target.deserializeArrayFast(signature, list)
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = list.size

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = 0

    override fun decodeValue(): Any = list.removeFirst()!!.also {
        lastInline = null
    }

    override fun decodeShort(): Short {
        if (isUnsigned) {
            return (decodeValue() as UShort).toShort()
        }
        return super.decodeShort()
    }

    override fun decodeInt(): Int {
        if (isUnsigned) {
            return (decodeValue() as UInt).toInt()
        }
        return super.decodeInt()
    }

    override fun decodeLong(): Long {
        if (isUnsigned) {
            return (decodeValue() as ULong).toLong()
        }
        return super.decodeLong()
    }
}

internal abstract class BaseMessageDecoder : AbstractDecoder() {

    protected var lastInline: SerialDescriptor? = null
    protected val isUnsigned: Boolean
        get() = (lastInline?.serialName?.replace("kotlin.", "") in unsignedNames).also {
            lastInline = null
        }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        lastInline = descriptor
        return this
    }
}
