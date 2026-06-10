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

import com.monkopedia.sdbus.PlainMessage.Companion.createPlainMessage
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

/**
 * Wraps a value of any D-Bus-serializable type into a [Variant].
 *
 * The serializer and signature are deduced automatically from the reified type [T].
 *
 * @param value The value to wrap
 * @return A [Variant] holding [value]
 */
inline fun <reified T : Any> Variant(value: T): Variant {
    val serializer = serializer<T>()
    return Variant(serializer, serializersModuleOf<T>(serializer), value)
}

/**
 * Tests whether the value currently held by this [Variant] has the D-Bus signature of type [T].
 *
 * @return `true` if the contained value's signature matches the signature deduced from [T]
 */
inline fun <reified T> Variant.containsValueOfType(): Boolean {
    val signature = signatureOf<T>()
    return signature.value == peekValueType()
}

/**
 * Represents the D-Bus variant type, a self-describing container that can hold a value of any
 * other D-Bus type along with its signature.
 *
 * Construct a variant from a value with the [Variant] factory function, and read the value back
 * with [get]. Use [containsValueOfType] or [peekValueType] to inspect the contained type before
 * extracting it.
 */
@Serializable(Variant.Companion::class)
class Variant constructor() {
    private val msg: PlainMessage = createPlainMessage()

    internal constructor(message: Message) : this() {
        // Do nothing, message wil deserialize
    }

    /**
     * Constructs a variant by serializing [value] using the given [strategy] and [module].
     *
     * @param strategy Serialization strategy used to encode [value]
     * @param module Serializers module providing any contextual serializers needed
     * @param value The value to wrap in the variant
     */
    constructor(
        strategy: SerializationStrategy<*>,
        module: SerializersModule,
        value: Any
    ) : this() {
        msg.openVariant(strategy.descriptor.asSignature.value)
        @Suppress("UNCHECKED_CAST")
        msg.serialize(strategy as SerializationStrategy<Any>, module, value)
        msg.closeVariant()
        msg.seal()
    }

    /**
     * Extracts and deserializes the value held by this variant as type [T].
     *
     * The serializer and expected signature are deduced from the reified type [T]. The contained
     * value must match that type/signature.
     *
     * @return The deserialized value
     */
    inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        return get(
            serializer,
            serializersModuleOf(serializer),
            signatureOf<T>()
        )
    }

    @PublishedApi
    internal fun <T : Any> get(
        type: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T {
        msg.rewind(false)

        msg.enterVariant(signature.value)
        @Suppress("UNCHECKED_CAST")
        val v: T = msg.deserialize(type, module)
        msg.exitVariant()
        return v
    }

    /** Whether this variant holds no value. */
    val isEmpty: Boolean
        get() = msg.isEmpty

    /**
     * Serializes the value held by this variant into [msg].
     *
     * @param msg Destination message to append the variant's contents to
     * @throws [com.monkopedia.sdbus.Error] if this variant is empty
     */
    fun serializeTo(msg: Message) {
        if (isEmpty) {
            throw createError(-1, "Cannot serialize an empty variant")
        }
        this.msg.rewind(true)
        this.msg.copyTo(msg, true)
    }

    /**
     * Populates this variant by reading a variant value from [msg].
     *
     * @param msg Source message to read the variant's contents from
     */
    fun deserializeFrom(msg: Message) {
        msg.copyTo(this.msg, false)
        this.msg.seal()
        this.msg.rewind(false)
    }

    /**
     * Returns the D-Bus signature string of the value currently held by this variant, or `null`
     * if it cannot be determined.
     */
    fun peekValueType(): String? {
        msg.rewind(false)
        return msg.peekType().contents
    }

    override fun toString(): String = "Variant(${peekValueType()})"

    /** Serializer for [Variant]. Variants are only (de)serializable within the sdbus machinery. */
    companion object : KSerializer<Variant> {
        const val SERIAL_NAME = "sdbus.Variant"
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SERIAL_NAME)

        override fun deserialize(decoder: Decoder): Variant {
            error("Not serializable outside sdbus")
        }

        override fun serialize(encoder: Encoder, value: Variant) =
            error("Not serializable outside sdbus")
    }
}

/********************************************/
/**
 * @class ObjectPath
 *
 * Strong type representing the D-Bus object path
 *
 ***********************************************/
@Serializable(ObjectPath.Companion::class)
@kotlin.jvm.JvmInline
value class ObjectPath(
    /** The raw object path string, e.g. `/com/example/Foo`. */
    val value: String
) {
    override fun toString(): String = value

    companion object : KSerializer<ObjectPath> {
        const val SERIAL_NAME = "sdbus.ObjectPath"
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): ObjectPath =
            decoder.decodeInline(descriptor).decodeString().let(::ObjectPath)

        override fun serialize(encoder: Encoder, value: ObjectPath) =
            encoder.encodeInline(descriptor).encodeString(value.value)
    }
}

/********************************************/
/**
 * @class BusName
 *
 * Strong type representing the D-Bus bus/service/connection name
 *
 ***********************************************/
@Serializable(BusName.Companion::class)
@kotlin.jvm.JvmInline
value class BusName(
    /** The raw bus/service name string, e.g. `com.example.Service`. */
    val value: String
) {
    override fun toString(): String = value

    companion object : KSerializer<BusName> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("sdbus.BusName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): BusName =
            decoder.decodeInline(descriptor).decodeString().let(::BusName)

        override fun serialize(encoder: Encoder, value: BusName) {
            encoder.encodeInline(descriptor).encodeString(value.value)
        }
    }
}

/** Alias for [BusName], used where a well-known service name is expected. */
typealias ServiceName = BusName

/**
 * Strong type representing the D-Bus interface name
 */
@Serializable(InterfaceName.Companion::class)
@kotlin.jvm.JvmInline
value class InterfaceName(
    /** The raw interface name string, e.g. `org.freedesktop.DBus.Properties`. */
    val value: String
) {
    override fun toString(): String = value

    companion object : KSerializer<InterfaceName> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("sdbus.InterfaceName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): InterfaceName =
            decoder.decodeInline(descriptor).decodeString().let(::InterfaceName)

        override fun serialize(encoder: Encoder, value: InterfaceName) {
            encoder.encodeInline(descriptor).encodeString(value.value)
        }
    }
}

/**
 * Strong type representing the D-Bus member name
 */
@Serializable(MemberName.Companion::class)
@kotlin.jvm.JvmInline
value class MemberName(
    /** The raw member name string identifying a method, signal, or property. */
    val value: String
) {
    override fun toString(): String = value

    companion object : KSerializer<MemberName> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("sdbus.MemberName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): MemberName =
            decoder.decodeInline(descriptor).decodeString().let(::MemberName)

        override fun serialize(encoder: Encoder, value: MemberName) {
            encoder.encodeInline(descriptor).encodeString(value.value)
        }
    }
}

/** Alias for [MemberName], used where a method name is expected. */
typealias MethodName = MemberName

/** Alias for [MemberName], used where a signal name is expected. */
typealias SignalName = MemberName

/** Alias for [MemberName], used where a property name is expected. */
typealias PropertyName = MemberName

/**
 * Strong type representing a D-Bus type signature string (e.g. `"a{sv}"`).
 */
@Serializable(Signature.Companion::class)
@kotlin.jvm.JvmInline
value class Signature(
    /** The raw D-Bus signature string. */
    val value: String
) {

    override fun toString(): String = value

    /** Returns a new signature with [other] appended to this signature's string. */
    operator fun plus(other: String): Signature = Signature(value + other)

    companion object : KSerializer<Signature> {
        const val SERIAL_NAME = "sdbus.Signature"
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Signature =
            decoder.decodeInline(descriptor).decodeString().let(::Signature)

        override fun serialize(encoder: Encoder, value: Signature) =
            encoder.encodeInline(descriptor).encodeString(value.value)
    }
}

/**
 * UnixFd is a representation of file descriptor D-Bus type that owns
 * the underlying fd, provides access to it, and closes the fd when
 * the UnixFd goes out of scope.
 *
 * UnixFd can be default constructed (owning invalid fd), or constructed from
 * an explicitly provided fd by either duplicating or adopting that fd as-is.
 */
expect class UnixFd(fd: Int, adoptFd: Unit) : Resource {

    constructor(fd: Int = -1)
    constructor(other: UnixFd)

    override fun release()

    companion object {
        val SERIAL_NAME: String
    }
}
