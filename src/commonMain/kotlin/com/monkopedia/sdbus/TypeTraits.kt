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
@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.sdbus

import kotlin.reflect.KType
import kotlin.reflect.typeOf
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

/** Handler invoked with an incoming [MethodCall] for a method exported by an [Object]. */
typealias MethodCallback = (msg: MethodCall) -> Unit

/** Handler invoked with the [MethodReply] (or [SdbusException]) of an asynchronous method call. */
internal typealias AsyncReplyHandler = (reply: MethodReply, error: SdbusException?) -> Unit

/** Handler invoked with an incoming [Signal]. */
typealias SignalHandler = (signal: Signal) -> Unit

/** Handler invoked with an incoming raw [Message], e.g. for match rules. */
typealias MessageHandler = (msg: Message) -> Unit

/** Handler invoked with a [PropertySetCall] when a remote caller writes a property. */
typealias PropertySetCallback = (msg: PropertySetCall) -> Unit

/** Handler invoked with a [PropertyGetReply] to populate a property value for a remote caller. */
typealias PropertyGetCallback = (msg: PropertyGetReply) -> Unit

/**
 * Computes the D-Bus signature for the reified type [T].
 *
 * @return The [SdbusSig] describing how [T] maps onto the D-Bus type system
 */
inline fun <reified T> signatureOf(): SdbusSig = signatureOf(typeOf<T>())

/** The D-Bus signature corresponding to this serial descriptor. */
internal val SerialDescriptor.asSignature: SdbusSig
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

/**
 * Computes the D-Bus signature for a runtime Kotlin [type].
 *
 * @param type The Kotlin type to map onto the D-Bus type system
 * @return The corresponding [SdbusSig]
 */
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

/**
 * Represents a D-Bus type signature in a structured form.
 *
 * This is an opaque handle from the public API's point of view; obtain instances via
 * [signatureOf] and pass them to the APIs that accept them.
 */
expect sealed class SdbusSig() {
    @PublishedApi
    internal abstract val value: String

    @PublishedApi
    internal abstract val valid: Boolean

    @PublishedApi
    internal abstract val isTrivial: Boolean
}

/** The signature of a type that has no valid D-Bus representation. */
internal data object InvalidSig : SdbusSig() {
    override val value: String
        get() = ""
    override val valid: Boolean
        get() = false
    override val isTrivial: Boolean
        get() = false
}

/**
 * The signature of a D-Bus struct, i.e. an ordered group of element signatures.
 *
 * @property signatures Signatures of the struct's elements, in order
 */
internal data class StructSig(val signatures: List<SdbusSig>) : SdbusSig() {

    /** The concatenated element signatures without the enclosing parentheses. */
    val contents: String
        get() = signatures.joinToString("") { it.value }
    override val value: String
        get() = "($contents)"
    override val valid: Boolean = true
    override val isTrivial: Boolean = false
}

/**
 * The signature of a D-Bus dictionary (array of key/value dict entries).
 *
 * @property t1 Signature of the key type
 * @property t2 Signature of the value type
 */
internal data class MapSig(val t1: SdbusSig, val t2: SdbusSig) : SdbusSig() {
    /** The concatenated key and value signatures. */
    val contents: String
        get() = "${t1.value}${t2.value}"

    /** The dict-entry signature, e.g. `{sv}`. */
    val dictValue: String
        get() = "{$contents}"
    override val value: String
        get() = "a{$contents}"
    override val valid: Boolean
        get() = true
    override val isTrivial: Boolean
        get() = false
}

/**
 * The signature of a D-Bus array.
 *
 * @property element Signature of the array's element type
 */
internal data class ListSig(val element: SdbusSig) : SdbusSig() {
    override val value: String
        get() = "a${element.value}"
    override val valid: Boolean
        get() = true
    override val isTrivial: Boolean
        get() = false
}

/** Signature for the absence of a value (an empty body), mapping from Kotlin `Unit`. */
internal expect val VoidSig: SdbusSig

/** Signature for the D-Bus `BOOLEAN` type (`b`). */
internal expect val BoolSig: SdbusSig

/** Signature for the D-Bus `BYTE` type (`y`). */
internal expect val UByteSig: SdbusSig

/** Signature for the D-Bus `INT16` type (`n`). */
internal expect val ShortSig: SdbusSig

/** Signature for the D-Bus `UINT16` type (`q`). */
internal expect val UShortSig: SdbusSig

/** Signature for the D-Bus `INT32` type (`i`). */
internal expect val IntSig: SdbusSig

/** Signature for the D-Bus `UINT32` type (`u`). */
internal expect val UIntSig: SdbusSig

/** Signature for the D-Bus `INT64` type (`x`). */
internal expect val LongSig: SdbusSig

/** Signature for the D-Bus `UINT64` type (`t`). */
internal expect val ULongSig: SdbusSig

/** Signature for the D-Bus `DOUBLE` type (`d`). */
internal expect val DoubleSig: SdbusSig

/** Signature for the D-Bus `STRING` type (`s`). */
internal expect val StringSig: SdbusSig

/** Signature for the D-Bus `OBJECT_PATH` type (`o`). */
internal expect val ObjectPathSig: SdbusSig

/** Signature for the D-Bus `SIGNATURE` type (`g`). */
internal expect val SignatureSig: SdbusSig

/** Signature for the D-Bus `UNIX_FD` type (`h`). */
internal expect val UnixFdSig: SdbusSig

/** Signature for the D-Bus `VARIANT` type (`v`). */
internal expect val VariantSig: SdbusSig
