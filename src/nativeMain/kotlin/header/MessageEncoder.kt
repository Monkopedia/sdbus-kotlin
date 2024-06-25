@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.sdbus.header

import kotlinx.cinterop.CVariable
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
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
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

private val unsignedNames = listOf(
    "UInt",
    "ULong",
    "UByte",
    "UShort"
)

internal class SerialDescriptorStack {
    private val backingStack = mutableListOf<SerialDescriptor>()
    var head: SerialDescriptor? = null
        private set

    val peek: SerialDescriptor
        get() = head ?: error("Stack is empty")

    private var signatureImpl: signature_of? = null
    val signature: signature_of
        get() = signatureImpl ?: peek.asSignature.also {
            signatureImpl = it
        }

    fun push(value: SerialDescriptor) {
        head?.let(backingStack::add)
        head = value
        signatureImpl = null
    }

    fun pop(value: SerialDescriptor) {
        require(value == head) { "Wrong item being popped" }
        head = backingStack.removeLastOrNull()
        signatureImpl = null
    }
}

class MessageEncoder(
    val target: Message,
    override val serializersModule: SerializersModule
) : AbstractEncoder() {

    private var lastInline: SerialDescriptor? = null
    private val isUnsigned: Boolean
        get() = (lastInline?.serialName?.replace("kotlin.", "") in unsignedNames).also {
            lastInline = null
        }
    private val structureStack = SerialDescriptorStack()

    override fun encodeNotNullMark() {
        error("Nullables are not allowed for serialization")
    }

    override fun encodeNull() {
        error("Nullables are not allowed for serialization")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return when (descriptor.kind) {
            LIST -> {
                val signature = (descriptor.asSignature as signature_of_list).element
                if (signature.is_trivial_dbus_type && signature != signature_of_bool) {
                    val converter = (signature as signature_of_primitive<*, *>).converter!!
                    ListEncoder(target, serializersModule, signature, converter)
                } else {
                    structureStack.push(descriptor)
                    target.openContainer(signature.value)
                    this
                }
            }

            MAP -> {
                structureStack.push(descriptor)
                val sig = structureStack.signature as signature_of_map
                target.openContainer(sig.dict_sig)
                this
            }

            CLASS,
            OBJECT -> {
                structureStack.push(descriptor)
                val sig = structureStack.signature as signature_of_struct
                target.openStruct(sig.contents)
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
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        when (descriptor.kind) {
            LIST, MAP -> target.closeContainer()
            CLASS, OBJECT -> target.closeStruct()
            else -> error("This should be unreachable")
        }
        structureStack.pop(descriptor)
    }

    override fun encodeBoolean(value: Boolean) {
        target.append(value)
    }

    override fun encodeByte(value: Byte) {
        if (isUnsigned) {
            target.append(value.toUByte())
        } else {
            target.append(value.toUByte())
        }
    }

    override fun encodeChar(value: Char) {
        target.append(value.code.toUByte())
        lastInline = null
    }

    override fun encodeDouble(value: Double) {
        target.append(value)
        lastInline = null
    }

    override fun encodeFloat(value: Float) {
        target.append(value.toDouble())
        lastInline = null
    }

    override fun encodeShort(value: Short) {
        if (isUnsigned) {
            target.append(value.toUShort())
        } else {
            target.append(value)
        }
    }

    override fun encodeInt(value: Int) {
        if (isUnsigned) {
            target.append(value.toUInt())
        } else {
            target.append(value)
        }
    }

    override fun encodeLong(value: Long) {
        if (isUnsigned) {
            target.append(value.toULong())
        } else {
            target.append(value)
        }
    }

    override fun encodeString(value: String) {
        when (lastInline?.serialName) {
            ObjectPath.SERIAL_NAME -> target.appendObjectPath(value)
            Signature.SERIAL_NAME -> target.appendSignature(value)
            else -> target.append(value)
        }
        lastInline = null
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        if (serializer.descriptor.serialName == "kotlin.Unit") {
            return
        }
        when (serializer.descriptor.serialName) {
            UnixFd.SERIAL_NAME -> target.append(value as UnixFd)
            Variant.SERIAL_NAME -> target.append(value as Variant)
            else -> super.encodeSerializableValue(serializer, value)
        }
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        if (descriptor == structureStack.head && descriptor.kind == MAP && ((index and 1) == 0)) {
            val signature = structureStack.signature as signature_of_map
            target.openDictEntry(signature.contents)
        }
        when (descriptor.serialName) {
            UnixFd.SERIAL_NAME -> target.append(value as UnixFd)
            Variant.SERIAL_NAME -> target.append(value as Variant)
            else -> super.encodeSerializableElement(descriptor, index, serializer, value)
        }
        if (descriptor == structureStack.head && descriptor.kind == MAP && ((index and 1) == 1)) {
            target.closeDictEntry()
        }
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        target.append(index)
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        lastInline = descriptor
        return this
    }
}

internal class ListEncoder<K, N : CVariable>(
    private val target: Message,
    override val serializersModule: SerializersModule,
    private val signature: signature_of,
    private val converter: NativeTypeConverter<K, N>
) : AbstractEncoder() {
    private val list = mutableListOf<K>()

    override fun encodeValue(value: Any) {
        @Suppress("UNCHECKED_CAST")
        list.add((value as? K) ?: return)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        memScoped {
            val array = converter.allocNative(this, list)
            target.appendArray(signature.value[0], array, (list.size * converter.size).convert())
        }
    }
}
