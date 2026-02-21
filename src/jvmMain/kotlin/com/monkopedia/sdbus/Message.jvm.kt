package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmStaticDispatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.modules.SerializersModule

private fun <T> requireJvmCredential(value: T?, name: String): T = value
    ?: throw createError(-1, "$name failed: sender credentials unavailable on JVM")

actual sealed class Message {
    internal data class JvmVariantPayload(val signature: String, val value: Any?)
    internal data class Metadata(
        val interfaceName: String? = null,
        val memberName: String? = null,
        val sender: String? = null,
        val path: String? = null,
        val destination: String? = null,
        val credsPid: Int? = null,
        val credsUid: UInt? = null,
        val credsEuid: UInt? = null,
        val credsGid: UInt? = null,
        val credsEgid: UInt? = null,
        val credsSupplementaryGids: List<UInt>? = null,
        val selinuxContext: String? = null,
        val valid: Boolean = false,
        val empty: Boolean = true
    )

    internal var metadata: Metadata = Metadata()
    internal val payload: MutableList<Any?> = mutableListOf()
    private var readIndex: Int = 0
    private var openVariantFrame: Pair<Int, String>? = null
    private val enteredVariantValues: ArrayDeque<Any?> = ArrayDeque()

    private fun nextValue(operation: String): Any? {
        if (readIndex >= payload.size) {
            throw createError(-1, "$operation failed: no remaining payload")
        }
        return payload[readIndex++]
    }
    internal fun nextRawValue(operation: String): Any? = nextValue(operation)
    internal fun nextDeserializedValue(operation: String): Any? =
        if (enteredVariantValues.isNotEmpty()) {
            enteredVariantValues.first()
        } else {
            nextRawValue(operation)
        }

    internal actual fun append(item: Boolean): Unit = run { payload.add(item) }
    internal actual fun append(item: Short): Unit = run { payload.add(item) }
    internal actual fun append(item: Int): Unit = run { payload.add(item) }
    internal actual fun append(item: Long): Unit = run { payload.add(item) }
    internal actual fun append(item: UByte): Unit = run { payload.add(item) }
    internal actual fun append(item: UShort): Unit = run { payload.add(item) }
    internal actual fun append(item: UInt): Unit = run { payload.add(item) }
    internal actual fun append(item: ULong): Unit = run { payload.add(item) }
    internal actual fun append(item: Double): Unit = run { payload.add(item) }
    internal actual fun append(item: String): Unit = run { payload.add(item) }
    internal actual fun appendObjectPath(item: String): Unit = run { payload.add(item) }
    internal actual fun append(item: ObjectPath): Unit = run { payload.add(item.value) }
    internal actual fun append(item: Signature): Unit = run { payload.add(item.value) }
    internal actual fun appendSignature(item: String): Unit = run { payload.add(item) }
    internal actual fun append(item: UnixFd): Unit = run { payload.add(item.fd) }
    internal actual fun readBoolean(): Boolean {
        val value = nextValue("Message.readBoolean")
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> throw createError(-1, "Message.readBoolean failed: unexpected payload type")
        }
    }
    internal actual fun readShort(): Short = (nextValue("Message.readShort") as Number).toShort()
    internal actual fun readInt(): Int = (nextValue("Message.readInt") as Number).toInt()
    internal actual fun readLong(): Long = (nextValue("Message.readLong") as Number).toLong()
    internal actual fun readUByte(): UByte =
        (nextValue("Message.readUByte") as Number).toByte().toUByte()
    internal actual fun readUShort(): UShort =
        (nextValue("Message.readUShort") as Number).toShort().toUShort()
    internal actual fun readUInt(): UInt =
        (nextValue("Message.readUInt") as Number).toInt().toUInt()
    internal actual fun readULong(): ULong =
        (nextValue("Message.readULong") as Number).toLong().toULong()
    internal actual fun readDouble(): Double =
        (nextValue("Message.readDouble") as Number).toDouble()
    internal actual fun readString(): String = nextValue("Message.readString").toString()
    internal actual fun readObjectPath(): ObjectPath =
        ObjectPath(nextValue("Message.readObjectPath").toString())
    internal actual fun readSignature(): Signature =
        Signature(nextValue("Message.readSignature").toString())
    internal actual fun readUnixFd(): UnixFd =
        UnixFd((nextValue("Message.readUnixFd") as Number).toInt())
    internal actual fun readVariant(): Variant {
        val value = nextValue("Message.readVariant")
        return value as? Variant
            ?: throw createError(-1, "Message.readVariant failed: unsupported payload type")
    }
    internal actual fun openContainer(signature: String): Unit = Unit
    internal actual fun closeContainer(): Unit = Unit
    internal actual fun openDictEntry(signature: String): Unit = Unit
    internal actual fun closeDictEntry(): Unit = Unit
    internal actual fun openVariant(signature: String): Unit = run {
        check(openVariantFrame == null) { "Nested JVM variant frames are not supported" }
        openVariantFrame = payload.size to signature
    }
    internal actual fun closeVariant(): Unit = run {
        val (startIndex, signature) = openVariantFrame
            ?: throw createError(-1, "Message.closeVariant failed: no open variant")
        val value = payload.getOrNull(startIndex)
            ?: throw createError(-1, "Message.closeVariant failed: missing variant payload")
        payload.subList(startIndex, payload.size).clear()
        payload.add(JvmVariantPayload(signature, value))
        openVariantFrame = null
    }
    internal actual fun openStruct(signature: String): Unit = Unit
    internal actual fun closeStruct(): Unit = Unit
    internal actual fun enterContainer(signature: String): Unit = Unit
    internal actual fun exitContainer(): Unit = Unit
    internal actual fun enterDictEntry(signature: String): Unit = Unit
    internal actual fun exitDictEntry(): Unit = Unit
    internal actual fun enterVariant(signature: String): Unit = run {
        val value = nextValue("Message.enterVariant")
        val variant = value as? JvmVariantPayload
            ?: throw createError(-1, "Message.enterVariant failed: unsupported payload type")
        enteredVariantValues.addFirst(variant.value)
    }
    internal actual fun exitVariant(): Unit = run {
        if (enteredVariantValues.isEmpty()) {
            throw createError(-1, "Message.exitVariant failed: no entered variant")
        }
        enteredVariantValues.removeFirst()
    }
    internal actual fun enterStruct(signature: String): Unit = Unit
    internal actual fun exitStruct(): Unit = Unit
    internal actual operator fun invoke(): Boolean = false
    internal actual fun clearFlags(): Unit = Unit

    actual fun getInterfaceName(): String? = metadata.interfaceName
    actual fun getMemberName(): String? = metadata.memberName
    actual fun getSender(): String? = metadata.sender
    actual fun getPath(): String? = metadata.path
    actual fun getDestination(): String? = metadata.destination
    actual fun peekType(): Pair<Char?, String?> {
        val next = payload.getOrNull(readIndex) ?: return null to null
        val signature = inferSignature(next) ?: return null to null
        return signature.first() to signature.drop(1).ifEmpty { null }
    }
    actual val isValid: Boolean
        get() = metadata.valid
    actual val isEmpty: Boolean
        get() = payload.isEmpty()
    actual fun isAtEnd(complete: Boolean): Boolean = readIndex >= payload.size
    actual fun copyTo(destination: Message, complete: Boolean) {
        destination.payload.addAll(payload)
        destination.metadata = metadata
    }
    actual fun seal(): Unit = Unit
    actual fun rewind(complete: Boolean): Unit = run {
        readIndex = 0
        enteredVariantValues.clear()
    }
    actual fun getCredsPid(): Int = requireJvmCredential(metadata.credsPid, "Message.getCredsPid")
    actual fun getCredsUid(): UInt = requireJvmCredential(metadata.credsUid, "Message.getCredsUid")
    actual fun getCredsEuid(): UInt =
        requireJvmCredential(metadata.credsEuid, "Message.getCredsEuid")
    actual fun getCredsGid(): UInt = requireJvmCredential(metadata.credsGid, "Message.getCredsGid")
    actual fun getCredsEgid(): UInt =
        requireJvmCredential(metadata.credsEgid, "Message.getCredsEgid")
    actual fun getCredsSupplementaryGids(): List<UInt> = requireJvmCredential(
        metadata.credsSupplementaryGids,
        "Message.getCredsSupplementaryGids"
    )
    actual fun getSELinuxContext(): String = requireJvmCredential(
        metadata.selinuxContext,
        "Message.getSELinuxContext"
    )
}

private fun inferSignature(value: Any?): String? = when (value) {
    null -> null
    is Message.JvmVariantPayload -> "v${value.signature}"
    is Variant -> value.peekValueType()?.let { "v$it" }
    is Boolean -> "b"
    is Byte, is UByte -> "y"
    is Short -> "n"
    is UShort -> "q"
    is Int -> "i"
    is UInt -> "u"
    is Long -> "x"
    is ULong -> "t"
    is Float, is Double -> "d"
    is String -> "s"
    is ObjectPath -> "o"
    is Signature -> "g"
    is List<*> -> {
        val elementSig = value.firstNotNullOfOrNull(::inferSignature) ?: return "a"
        "a$elementSig"
    }

    is Array<*> -> {
        val elementSig = value.firstNotNullOfOrNull(::inferSignature) ?: return "a"
        "a$elementSig"
    }

    is Map<*, *> -> {
        val first = value.entries.firstOrNull() ?: return "a{}"
        val keySig = inferSignature(first.key) ?: return "a{}"
        val valueSig = inferSignature(first.value) ?: return "a{}"
        "a{$keySig$valueSig}"
    }

    else -> null
}

private fun isSignatureCompatible(expected: String, actual: String): Boolean {
    if (expected == actual) return true
    if (expected == "v" && actual.startsWith("v")) return true
    return false
}

private fun shouldEnforceSignature(expected: String): Boolean = when (expected) {
    "b",
    "y",
    "n",
    "q",
    "i",
    "u",
    "x",
    "t",
    "d",
    "s",
    "o",
    "g",
    "h" -> true

    else -> false
}

private fun coerceForDescriptor(value: Any?, descriptor: SerialDescriptor): Any? {
    val wrapped = when (descriptor.serialName) {
        ObjectPath.SERIAL_NAME -> when (value) {
            is ObjectPath -> value
            is String -> ObjectPath(value)
            else -> value
        }

        "sdbus.BusName" -> when (value) {
            is BusName -> value
            is String -> BusName(value)
            else -> value
        }

        "sdbus.InterfaceName" -> when (value) {
            is InterfaceName -> value
            is String -> InterfaceName(value)
            else -> value
        }

        "sdbus.MemberName" -> when (value) {
            is MemberName -> value
            is String -> MemberName(value)
            else -> value
        }

        Signature.SERIAL_NAME -> when (value) {
            is Signature -> value
            is String -> Signature(value)
            else -> value
        }

        UnixFd.SERIAL_NAME -> when (value) {
            is UnixFd -> UnixFd(value.fd)
            is Number -> UnixFd(value.toInt())
            else -> value
        }

        else -> value
    }
    if (wrapped !== value) return wrapped

    return when (descriptor.kind) {
        StructureKind.LIST -> {
            val expectsArray = descriptor.serialName == "kotlin.Array"
            if (expectsArray) {
                return when (value) {
                    is Array<*> -> value
                    else -> value
                }
            }
            val itemDescriptor = descriptor.getElementDescriptor(0)
            when (value) {
                is List<*> -> value.map { coerceForDescriptor(it, itemDescriptor) }
                is Array<*> -> value.map { coerceForDescriptor(it, itemDescriptor) }
                else -> value
            }
        }

        StructureKind.MAP -> {
            if (value !is Map<*, *>) return value
            val keyDescriptor = descriptor.getElementDescriptor(0)
            val valueDescriptor = descriptor.getElementDescriptor(1)
            value.entries.associate { (key, entryValue) ->
                coerceForDescriptor(key, keyDescriptor) to
                    coerceForDescriptor(entryValue, valueDescriptor)
            }
        }

        else -> value
    }
}

actual class MethodCall internal constructor() : Message() {
    internal var sentReply: MethodReply? = null
    private val replySent = CountDownLatch(1)

    actual fun send(timeout: ULong): MethodReply {
        val interfaceName = metadata.interfaceName
            ?: throw createError(-1, "MethodCall.send failed: missing interface name")
        val methodName = metadata.memberName
            ?: throw createError(-1, "MethodCall.send failed: missing method name")
        val path = metadata.path
            ?: throw createError(-1, "MethodCall.send failed: missing object path")
        val invoke: () -> Any? = {
            JvmStaticDispatch.invokeOrNull(
                objectPath = path,
                interfaceName = interfaceName,
                methodName = methodName,
                args = payload,
                destination = metadata.destination
            )
                ?: throw createError(
                    -1,
                    "MethodCall.send failed: no static binding for $path:$interfaceName.$methodName/${payload.size}"
                )
        }
        val result = if (timeout == 0uL) {
            invoke()
        } else {
            val value = AtomicReference<Any?>()
            val failure = AtomicReference<Throwable?>()
            val done = CountDownLatch(1)
            kotlin.concurrent.thread(
                start = true,
                isDaemon = true,
                name = "sdbus-jvm-send-timeout"
            ) {
                runCatching { invoke() }
                    .onSuccess { value.set(it) }
                    .onFailure { failure.set(it) }
                done.countDown()
            }
            val timeoutMillis = ((timeout + 999uL) / 1000uL).toLong().coerceAtLeast(1L)
            if (!done.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw createError(-1, "Method call timed out")
            }
            failure.get()?.let { throw it }
            value.get()
        }
        val values = when (result) {
            null,
            Unit -> emptyList()
            is JvmStaticDispatch.DispatchResult -> {
                result.reply.error?.let { throw it }
                if (!result.reply.isValid) {
                    throw createError(-1, "MethodCall.send failed: invalid reply")
                }
                result.reply.payload.toList()
            }

            else -> listOf(result)
        }
        return methodReplyFrom(
            Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = metadata.destination,
                path = path,
                destination = metadata.sender,
                valid = true,
                empty = values.isEmpty()
            ),
            values
        )
    }
    actual fun createReply(): MethodReply = MethodReply(this).also {
        it.metadata = Metadata(valid = true, empty = true)
    }
    actual fun createErrorReply(error: Error): MethodReply = MethodReply(this).also {
        it.error = error
        it.metadata = Metadata(valid = false, empty = true)
    }
    actual var dontExpectReply: Boolean = false

    internal fun markReplySent(reply: MethodReply) {
        sentReply = reply
        replySent.countDown()
    }

    internal fun awaitReply(timeoutMillis: Long): MethodReply? {
        if (sentReply != null) return sentReply
        if (replySent.await(timeoutMillis, TimeUnit.MILLISECONDS)) return sentReply
        return null
    }
}

actual class MethodReply internal constructor(private val parentCall: MethodCall? = null) :
    Message() {
    internal var error: Error? = null

    actual fun send(): Unit = run {
        if (metadata.valid) {
            metadata = metadata.copy(empty = payload.isEmpty())
        }
        parentCall?.markReplySent(this)
    }
}

actual class Signal internal constructor() : Message() {
    internal var sendAction: ((Signal) -> Unit)? = null

    actual fun setDestination(destination: String): Unit = run {
        metadata = metadata.copy(destination = destination)
    }

    actual fun send(): Unit = requireJvmCredential(
        sendAction,
        "Signal.send"
    )(this)
}

internal fun signalFromMetadata(
    metadata: Message.Metadata,
    sendAction: ((Signal) -> Unit)? = null
): Signal = Signal().also {
    it.metadata = metadata
    it.sendAction = sendAction
}

internal fun methodReplyFrom(metadata: Message.Metadata, payload: List<Any?>): MethodReply =
    MethodReply().also {
        it.metadata = metadata
        it.payload.addAll(payload)
    }

actual class PropertySetCall internal constructor() : Message()

actual class PropertyGetReply internal constructor() : Message()

actual class PlainMessage internal constructor() : Message() {
    actual companion object {
        actual fun createPlainMessage(): PlainMessage = PlainMessage()
    }
}

@PublishedApi
internal actual fun <T> Message.serialize(
    serializer: SerializationStrategy<T>,
    module: SerializersModule,
    arg: T
) {
    payload.add(
        when (arg) {
            is UnixFd -> arg.fd
            else -> arg
        }
    )
}

@PublishedApi
internal actual fun <T : Any> Message.deserialize(
    serializer: DeserializationStrategy<T>,
    module: SerializersModule
): T {
    if (serializer.descriptor.serialName == "kotlin.Unit") {
        @Suppress("UNCHECKED_CAST")
        return Unit as T
    }
    val value = nextDeserializedValue("Message.deserialize")
    val coerced = coerceForDescriptor(value, serializer.descriptor)
    val expectedSig = serializer.descriptor.asSignature.value
    val actualSig = inferSignature(coerced)
    if (
        actualSig != null &&
        shouldEnforceSignature(expectedSig) &&
        !isSignatureCompatible(expectedSig, actualSig)
    ) {
        throw createError(
            -1,
            "Message.deserialize failed: signature mismatch expected=$expectedSig actual=$actualSig"
        )
    }
    return coerced as? T
        ?: throw createError(
            -1,
            "Message.deserialize failed: unexpected payload type ${coerced?.let {
                it::class.simpleName
            }}"
        )
}

internal actual inline fun <T> Message.deserializeArrayFast(
    signature: SdbusSig,
    items: MutableList<T>
) {
    @Suppress("UNCHECKED_CAST")
    val values = when (val value = nextRawValue("Message.deserializeArrayFast")) {
        is List<*> -> value
        is Array<*> -> value.toList()
        else -> throw createError(
            -1,
            "Message.deserializeArrayFast failed: expected array-like payload"
        )
    }
    items.addAll(values as List<T>)
}
