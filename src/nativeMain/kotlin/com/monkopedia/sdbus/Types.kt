@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.PlainMessage.Companion.createPlainMessage
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveKind.INT
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer
import platform.posix.close
import platform.posix.dup
import platform.posix.errno

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
    private val msg_: PlainMessage = createPlainMessage()

    internal constructor(message: Message) : this() {
        // Do nothing, message wil deserialize
    }

    constructor(
        strategy: SerializationStrategy<*>,
        module: SerializersModule,
        value: Any
    ) : this() {
        msg_.openVariant(strategy.descriptor.asSignature.value)
        @Suppress("UNCHECKED_CAST")
        msg_.serialize(strategy as SerializationStrategy<Any>, module, value)
        msg_.closeVariant()
        msg_.seal()
    }

    inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        return get(
            serializer,
            serializersModuleOf(serializer),
            signatureOf<T>()
        )
    }

    fun <T : Any> get(
        type: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T {
        msg_.rewind(false)

        msg_.enterVariant(signature.value)
        @Suppress("UNCHECKED_CAST")
        val v: T = msg_.deserialize(type, module)
        msg_.exitVariant()
        return v
    }

    val isEmpty: Boolean
        get() = msg_.isEmpty

    fun serializeTo(msg: Message) {
        msg_.rewind(true)
        msg_.copyTo(msg, true)
    }

    fun deserializeFrom(msg: Message) {
        msg.copyTo(msg_, false)
        msg_.seal()
        msg_.rewind(false)
    }

    fun peekValueType(): String? {
        msg_.rewind(false)
        val (_, contents) = msg_.peekType()
        return contents
    }

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

/********************************************/
/**
 * @class InterfaceName
 *
 * Strong type representing the D-Bus interface name
 *
 ***********************************************/
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

/********************************************/
/**
 * @class MemberName
 *
 * Strong type representing the D-Bus member name
 *
 ***********************************************/
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

/********************************************/
/**
 * @class Signature
 *
 * Strong type representing the D-Bus object path
 *
 ***********************************************/
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

/********************************************/
/**
 * @struct UnixFd
 *
 * UnixFd is a representation of file descriptor D-Bus type that owns
 * the underlying fd, provides access to it, and closes the fd when
 * the UnixFd goes out of scope.
 *
 * UnixFd can be default constructed (owning invalid fd), or constructed from
 * an explicitly provided fd by either duplicating or adopting that fd as-is.
 *
 ***********************************************/
@Serializable(UnixFd.Companion::class)
class UnixFd(val fd: Int, adopt_fd: Unit) : Resource {
    private var wasReleased = false
    private val cleaner = createCleaner(fd) {
        if (it >= 0) {
            close(it)
        }
    }

    constructor(fd: Int = -1) : this(checkedDup(fd), Unit)
    constructor(other: UnixFd) : this(checkedDup(other.fd), Unit)

    val isValid: Boolean
        get() = fd >= 0 && !wasReleased

    override fun release() {
        close(fd)
        wasReleased = true
    }

    companion object : KSerializer<UnixFd> {

        const val SERIAL_NAME = "sdbus.UnixFD"

        private fun checkedDup(fd: Int): Int {
            if (fd < 0) {
                return fd
            }
            return dup(fd).also {
                if (it < 0) {
                    throw createError(errno, "dup failed")
                }
            }
        }

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(SERIAL_NAME, INT)

        override fun deserialize(decoder: Decoder): UnixFd =
            decoder.decodeInline(descriptor).decodeInt().let(::UnixFd)

        override fun serialize(encoder: Encoder, value: UnixFd) {
            encoder.encodeInline(descriptor).encodeInt(value.fd)
        }
    }
}
