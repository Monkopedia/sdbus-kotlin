@file:OptIn(ExperimentalForeignApi::class, ExperimentalSerializationApi::class)

package com.monkopedia.sdbus

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.cinterop.ExperimentalForeignApi
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
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.serializer

typealias MethodCallback = (msg: MethodCall) -> Unit
typealias AsyncReplyHandler = (reply: MethodReply, error: Error?) -> Unit
typealias SignalHandler = (signal: Signal) -> Unit
typealias MessageHandler = (msg: Message) -> Unit
typealias PropertySetCallback = (msg: PropertySetCall) -> Unit
typealias PropertyGetCallback = (msg: PropertyGetReply) -> Unit

inline fun <reified T> signatureOf(): SdbusSig = signatureOf(typeOf<T>())

val SerialDescriptor.asSignature: SdbusSig
    get() = signatureOf()

private fun SerialDescriptor.signatureOf(): SdbusSig {
    when (serialName) {
        UnixFd.SERIAL_NAME -> return UnixFdSig
        Variant.SERIAL_NAME -> return VariantSig
        Signature.SERIAL_NAME -> return SignatureSig
        ObjectPath.SERIAL_NAME -> return ObjectPathSig
        "kotlin.ULong" -> return ULongSig
        "kotlin.UInt" -> return UIntSig
        "kotlin.UShort" -> return UShortSig
        "kotlin.UByte" -> return UByteSig
        "kotlin.Unit" -> return VoidSig
    }
    return when (kind) {
        BOOLEAN -> BoolSig
        BYTE -> UByteSig
        CHAR -> UByteSig
        DOUBLE -> DoubleSig
        FLOAT -> DoubleSig
        ENUM,
        INT -> IntSig

        LONG -> LongSig
        SHORT -> ShortSig
        STRING -> StringSig
        LIST -> ListSig(getElementDescriptor(0).asSignature)
        MAP -> MapSig(
            getElementDescriptor(0).asSignature,
            getElementDescriptor(1).asSignature
        )

        CLASS,
        OBJECT -> {
            StructSig(elementDescriptors.map { it.asSignature })
        }

        OPEN,
        SEALED,
        CONTEXTUAL -> error("Unsupported")
    }
}

fun signatureOf(type: KType): SdbusSig {
    return when (type.classifier) {
        Unit::class -> VoidSig
        Boolean::class -> BoolSig
        UByte::class -> UByteSig
        Short::class -> ShortSig
        UShort::class -> UShortSig
        Int::class -> IntSig
        UInt::class -> UIntSig
        Long::class -> LongSig
        ULong::class -> ULongSig
        Double::class -> DoubleSig
        String::class -> StringSig
        CharSequence::class -> StringSig
        BusName::class -> StringSig
        InterfaceName::class -> StringSig
        MemberName::class -> StringSig
        Variant::class -> VariantSig

        ObjectPath::class -> ObjectPathSig
        Signature::class -> SignatureSig
        UnixFd::class -> UnixFdSig
        Map::class,
        AbstractMap::class,
        HashMap::class,
        LinkedHashMap::class,
        MutableMap::class,
        AbstractMutableMap::class -> {
            val (first, second) = type.arguments
            return MapSig(
                signatureOf(first.type ?: return InvalidSig),
                signatureOf(second.type ?: return InvalidSig)
            )
        }

        Array::class,
        List::class,
        MutableList::class,
        AbstractList::class,
        AbstractMutableList::class,
        ArrayList::class -> {
            val arg = type.arguments.firstOrNull()?.type
                ?: error("List type with no argument $type - ${type.classifier}")
            return ListSig(signatureOf(arg))
        }

        Pair::class -> {
            val arg1 = type.arguments.getOrNull(0)?.type
                ?: error("Pair type with no argument 1 $type - ${type.classifier}")
            val arg2 = type.arguments.getOrNull(1)?.type
                ?: error("Pair type with no argument 2 $type - ${type.classifier}")
            return StructSig(
                listOf(
                    signatureOf(arg1),
                    signatureOf(arg2)
                )
            )
        }

        Triple::class -> {
            val arg1 = type.arguments.getOrNull(0)?.type
                ?: error("Triple type with no argument 1 $type - ${type.classifier}")
            val arg2 = type.arguments.getOrNull(1)?.type
                ?: error("Triple type with no argument 2 $type - ${type.classifier}")
            val arg3 = type.arguments.getOrNull(2)?.type
                ?: error("Triple type with no argument 3 $type - ${type.classifier}")
            return StructSig(
                listOf(
                    signatureOf(arg1),
                    signatureOf(arg2),
                    signatureOf(arg3)
                )
            )
        }

        else -> serializer(type).descriptor.asSignature
    }
}

interface SdbusSig {
    val value: String
    val valid: Boolean
    val isTrivial: Boolean
}

object InvalidSig : SdbusSig {
    override val value: String
        get() = ""
    override val valid: Boolean
        get() = false
    override val isTrivial: Boolean
        get() = false
}

data class StructSig(val signatures: List<SdbusSig>) : SdbusSig {

    val contents: String
        get() = signatures.joinToString("") { it.value }
    override val value: String
        get() = "($contents)"
    override val valid: Boolean = true
    override val isTrivial: Boolean = false
}

data class MapSig(val t1: SdbusSig, val t2: SdbusSig) : SdbusSig {
    val contents: String
        get() = "${t1.value}${t2.value}"
    val dictValue: String
        get() = "{$contents}"
    override val value: String
        get() = "a{$contents}"
    override val valid: Boolean
        get() = true
    override val isTrivial: Boolean
        get() = false
}

data class ListSig(val element: SdbusSig) : SdbusSig {
    override val value: String
        get() = "a${element.value}"
    override val valid: Boolean
        get() = true
    override val isTrivial: Boolean
        get() = false
}

expect val VoidSig: SdbusSig

expect val BoolSig : SdbusSig

expect val UByteSig : SdbusSig

expect val ShortSig : SdbusSig

expect val UShortSig : SdbusSig

expect val IntSig : SdbusSig

expect val UIntSig : SdbusSig

expect val LongSig: SdbusSig

expect val ULongSig : SdbusSig

expect val DoubleSig : SdbusSig

expect val StringSig: SdbusSig

expect val ObjectPathSig : SdbusSig

expect val SignatureSig : SdbusSig

expect val UnixFdSig : SdbusSig

expect val VariantSig: SdbusSig
