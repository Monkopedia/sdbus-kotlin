/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
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

inline fun <reified T : Any> Variant(value: T): Variant {
    val serializer = serializer<T>()
    return Variant(serializer, serializersModuleOf<T>(serializer), value)
}

inline fun <reified T> Variant.containsValueOfType(): Boolean {
    val signature = signatureOf<T>()
    return signature.value == peekValueType()
}

@Serializable(Variant.Companion::class)
class Variant constructor() {
    private val msg: PlainMessage = createPlainMessage()

    internal constructor(message: Message) : this() {
        // Do nothing, message wil deserialize
    }

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

    val isEmpty: Boolean
        get() = msg.isEmpty

    fun serializeTo(msg: Message) {
        this.msg.rewind(true)
        this.msg.copyTo(msg, true)
    }

    fun deserializeFrom(msg: Message) {
        msg.copyTo(this.msg, false)
        this.msg.seal()
        this.msg.rewind(false)
    }

    fun peekValueType(): String? {
        msg.rewind(false)
        val (_, contents) = msg.peekType()
        return contents
    }

    override fun toString(): String = "Variant(${peekValueType()})"

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
value class ObjectPath(val value: String) {
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
value class BusName(val value: String) {
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

typealias ServiceName = BusName

/**
 * Strong type representing the D-Bus interface name
 */
@Serializable(InterfaceName.Companion::class)
value class InterfaceName(val value: String) {
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
value class MemberName(val value: String) {
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

typealias MethodName = MemberName
typealias SignalName = MemberName
typealias PropertyName = MemberName

/**
 * Strong type representing the D-Bus object path
 */
@Serializable(Signature.Companion::class)
value class Signature(val value: String) {

    override fun toString(): String = value

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
