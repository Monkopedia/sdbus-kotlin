package com.monkopedia.sdbus

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

// JVM counterpart of the native MessageEncoder/MessageDecoder pair (nativeMain/internal):
// instead of writing into an sd-bus message, values are encoded into/decoded from the JVM
// backend's payload value tree, where a D-Bus struct is represented by
// [Message.JvmStructPayload] (mirroring how [Message.JvmVariantPayload] represents variants).
// This is what allows @Serializable struct types to survive the trip through the wire
// marshaller, which needs structs decomposed into positional values (issue #71), and what
// allows multi-out (grouped) replies to consume one payload value per out-arg (issue #74,
// via the common degrouping machinery in DegroupingReturns.kt, which bypasses
// beginStructure/endStructure on this encoder/decoder).

private val unsignedNames = listOf(
    "UInt",
    "ULong",
    "UByte",
    "UShort"
)

/**
 * Encodes [arg] into the JVM payload value tree using its serializer: primitives and the
 * sdbus value types stay as themselves, lists/maps become List/Map, and struct-kind classes
 * become [Message.JvmStructPayload] holding their decomposed field values. A degrouped
 * serializer (multi-out reply) produces one top-level value per field.
 */
internal fun <T> encodeToJvmValues(
    serializer: SerializationStrategy<T>,
    module: SerializersModule,
    arg: T
): List<Any?> {
    val values = mutableListOf<Any?>()
    JvmValueEncoder(module, values::add).encodeSerializableValue(serializer, arg)
    return values
}

internal class JvmValueEncoder(
    override val serializersModule: SerializersModule,
    private val sink: (Any?) -> Unit,
    private val onEnd: () -> Unit = {}
) : AbstractEncoder() {

    private var lastInline: SerialDescriptor? = null
    private val isUnsigned: Boolean
        get() = (lastInline?.serialName?.removePrefix("kotlin.") in unsignedNames).also {
            lastInline = null
        }

    override fun encodeNotNullMark() {
        error("Nullables are not allowed for serialization")
    }

    override fun encodeNull() {
        error("Nullables are not allowed for serialization")
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        lastInline = descriptor
        return this
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        when (descriptor.kind) {
            StructureKind.LIST -> {
                val items = mutableListOf<Any?>()
                JvmValueEncoder(serializersModule, items::add) { sink(items.toList()) }
            }

            StructureKind.MAP -> {
                val entries = mutableListOf<Any?>()
                JvmValueEncoder(serializersModule, entries::add) {
                    val map = LinkedHashMap<Any?, Any?>()
                    var index = 0
                    while (index + 1 < entries.size) {
                        map[entries[index]] = entries[index + 1]
                        index += 2
                    }
                    sink(map)
                }
            }

            StructureKind.CLASS,
            StructureKind.OBJECT -> {
                val signature = descriptor.asSignature.value
                val fields = mutableListOf<Any?>()
                JvmValueEncoder(serializersModule, fields::add) {
                    sink(Message.JvmStructPayload(signature, fields.toList()))
                }
            }

            else -> error("Unsupported structure kind ${descriptor.kind} for serialization")
        }

    override fun endStructure(descriptor: SerialDescriptor) = onEnd()

    override fun encodeBoolean(value: Boolean) {
        lastInline = null
        sink(value)
    }

    // D-Bus has no signed byte; BYTE-kind values travel as 'y' (see TypeTraits signatureOf),
    // matching the native encoder which always appends bytes as UByte.
    override fun encodeByte(value: Byte) {
        lastInline = null
        sink(value.toUByte())
    }

    override fun encodeChar(value: Char) {
        lastInline = null
        sink(value.code.toUByte())
    }

    override fun encodeShort(value: Short) {
        sink(if (isUnsigned) value.toUShort() else value)
    }

    override fun encodeInt(value: Int) {
        sink(if (isUnsigned) value.toUInt() else value)
    }

    override fun encodeLong(value: Long) {
        sink(if (isUnsigned) value.toULong() else value)
    }

    override fun encodeFloat(value: Float) {
        lastInline = null
        sink(value.toDouble())
    }

    override fun encodeDouble(value: Double) {
        lastInline = null
        sink(value)
    }

    override fun encodeString(value: String) {
        when (lastInline?.serialName) {
            ObjectPath.SERIAL_NAME -> sink(ObjectPath(value))
            Signature.SERIAL_NAME -> sink(Signature(value))
            else -> sink(value)
        }
        lastInline = null
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        lastInline = null
        sink(index)
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        when (serializer.descriptor.serialName) {
            "kotlin.Unit" -> return
            UnixFd.SERIAL_NAME -> sink(value as UnixFd)
            Variant.SERIAL_NAME -> sink(value as Variant)
            else -> super.encodeSerializableValue(serializer, value)
        }
    }
}

/**
 * Decodes a value of [serializer]'s type by pulling payload values from [nextSlot] and
 * recursing into container values. The inverse of [JvmValueEncoder]; see the file comment.
 */
internal fun <T> decodeFromJvmValues(
    serializer: DeserializationStrategy<T>,
    module: SerializersModule,
    nextSlot: (String) -> Any?
): T = serializer.deserialize(JvmValueDecoder(module, nextSlot))

internal class JvmValueDecoder(
    override val serializersModule: SerializersModule,
    private val nextSlot: (String) -> Any?,
    private val collectionSize: Int = -1
) : AbstractDecoder() {

    private var lastInline: SerialDescriptor? = null
    private var elementIndex = 0

    private fun next(operation: String): Any? = nextSlot(operation)

    override fun decodeNotNullMark(): Boolean {
        error("Nullables are not allowed for serialization")
    }

    override fun decodeNull(): Nothing? {
        error("Nullables are not allowed for serialization")
    }

    override fun decodeSequentially(): Boolean = collectionSize >= 0

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = collectionSize

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (elementIndex < descriptor.elementsCount) {
            elementIndex++
        } else {
            CompositeDecoder.DECODE_DONE
        }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        lastInline = descriptor
        return this
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when (descriptor.kind) {
            StructureKind.LIST -> {
                val slot = next("Message.deserialize")
                val items = jvmValueAsList(slot)
                    ?: throw structMismatchError(descriptor, slot)
                JvmValueDecoder(serializersModule, items.slotSource(), collectionSize = items.size)
            }

            StructureKind.MAP -> {
                val slot = next("Message.deserialize")
                val map = slot as? Map<*, *> ?: throw structMismatchError(descriptor, slot)
                val flattened = map.entries.flatMap { listOf(it.key, it.value) }
                JvmValueDecoder(
                    serializersModule,
                    flattened.slotSource(),
                    collectionSize = map.size
                )
            }

            StructureKind.CLASS,
            StructureKind.OBJECT -> {
                val slot = next("Message.deserialize")
                val fields = (slot as? Message.JvmStructPayload)?.fields
                    ?: throw structMismatchError(descriptor, slot)
                JvmValueDecoder(serializersModule, fields.slotSource())
            }

            else -> error("Unsupported structure kind ${descriptor.kind} for serialization")
        }

    override fun endStructure(descriptor: SerialDescriptor) = Unit

    override fun decodeBoolean(): Boolean = when (val slot = next("Message.readBoolean")) {
        is Boolean -> slot
        is Number -> slot.toInt() != 0
        else -> throw slotTypeError("Message.readBoolean", slot)
    }.also { lastInline = null }

    override fun decodeByte(): Byte = slotLong("Message.readByte").toByte()

    override fun decodeChar(): Char = Char(slotLong("Message.readChar").toInt()).also {
        lastInline = null
    }

    override fun decodeShort(): Short = slotLong("Message.readShort").toShort()

    override fun decodeInt(): Int = slotLong("Message.readInt").toInt()

    override fun decodeLong(): Long = slotLong("Message.readLong")

    override fun decodeFloat(): Float = slotDouble("Message.readFloat").toFloat()

    override fun decodeDouble(): Double = slotDouble("Message.readDouble")

    override fun decodeString(): String {
        lastInline = null
        return when (val slot = next("Message.readString")) {
            is String -> slot
            is ObjectPath -> slot.value
            is Signature -> slot.value
            is BusName -> slot.value
            is InterfaceName -> slot.value
            is MemberName -> slot.value
            else -> slot?.toString() ?: throw slotTypeError("Message.readString", slot)
        }
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        slotLong("Message.readEnum").toInt()

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T =
        when (deserializer.descriptor.serialName) {
            "kotlin.Unit" -> Unit as T
            UnixFd.SERIAL_NAME -> slotUnixFd() as T
            Variant.SERIAL_NAME -> slotVariant() as T
            else -> super.decodeSerializableValue(deserializer)
        }

    private fun slotUnixFd(): UnixFd = when (val slot = next("Message.readUnixFd")) {
        is UnixFd -> slot
        is Number -> UnixFd(slot.toInt())
        else -> throw slotTypeError("Message.readUnixFd", slot)
    }

    private fun slotVariant(): Variant = when (val slot = next("Message.readVariant")) {
        is Variant -> slot
        is Message.JvmVariantPayload -> PlainMessage.createPlainMessage().let { message ->
            message.payload.add(slot)
            Variant().apply { deserializeFrom(message) }
        }

        else -> throw slotTypeError("Message.readVariant", slot)
    }

    private fun slotLong(operation: String): Long {
        lastInline = null
        return when (val slot = next(operation)) {
            is Number -> slot.toLong()
            is UByte -> slot.toLong()
            is UShort -> slot.toLong()
            is UInt -> slot.toLong()
            is ULong -> slot.toLong()
            is Boolean -> if (slot) 1L else 0L
            else -> throw slotTypeError(operation, slot)
        }
    }

    private fun slotDouble(operation: String): Double {
        lastInline = null
        return when (val slot = next(operation)) {
            is Number -> slot.toDouble()
            else -> throw slotTypeError(operation, slot)
        }
    }

    private fun slotTypeError(operation: String, slot: Any?): SdbusException = createError(
        -1,
        "$operation failed: unexpected payload type ${slot?.let { it::class.simpleName }}"
    )

    // ENXIO mirrors the native backend, which fails entering a mismatched container with
    // -ENXIO from sd_bus_message_enter_container.
    private fun structMismatchError(descriptor: SerialDescriptor, slot: Any?): SdbusException {
        val expected = runCatching { descriptor.asSignature.value }.getOrNull() ?: "?"
        val actual = inferJvmPayloadSignature(slot) ?: slot?.let { it::class.simpleName } ?: "null"
        return createError(
            6,
            "Message.deserialize failed: signature mismatch expected=$expected actual=$actual"
        )
    }
}

private fun List<Any?>.slotSource(): (String) -> Any? {
    val iterator = iterator()
    return { operation ->
        if (!iterator.hasNext()) {
            throw createError(-1, "$operation failed: no remaining payload")
        }
        iterator.next()
    }
}

/** Normalizes the JVM payload representations of D-Bus arrays into a [List]. */
internal fun jvmValueAsList(value: Any?): List<Any?>? = when (value) {
    is List<*> -> value
    is Array<*> -> value.toList()
    is ByteArray -> value.toList()
    is ShortArray -> value.toList()
    is IntArray -> value.toList()
    is LongArray -> value.toList()
    is BooleanArray -> value.toList()
    is FloatArray -> value.toList()
    is DoubleArray -> value.toList()
    else -> null
}

/**
 * Whether this payload value (or any value nested in it) is a wire-shaped struct
 * representation that needs structured decoding to become its Kotlin type again.
 */
internal fun containsJvmStructPayload(value: Any?): Boolean = when (value) {
    is Message.JvmStructPayload -> true
    is Message.JvmVariantPayload -> containsJvmStructPayload(value.value)
    is List<*> -> value.any(::containsJvmStructPayload)
    is Array<*> -> value.any(::containsJvmStructPayload)
    is Map<*, *> -> value.entries.any {
        containsJvmStructPayload(it.key) || containsJvmStructPayload(it.value)
    }

    else -> false
}
