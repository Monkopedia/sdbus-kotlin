@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.IConnection
import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.ManagedObjectAdaptor
import com.monkopedia.sdbus.MemberName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectManagerAdaptor
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesAdaptor
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.createError
import com.monkopedia.sdbus.createObject
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.usleep

class ObjectManagerTestAdaptor(override val obj: IObject) : ObjectManagerAdaptor {
    init {
        registerObjectManagerAdaptor()
    }

    constructor(connection: com.monkopedia.sdbus.IConnection, path: ObjectPath) :
        this(createObject(connection, path))
}

class TestAdaptor(connection: com.monkopedia.sdbus.IConnection, path: ObjectPath) :
    IntegrationTestsAdaptor(createObject(connection, path)),
    PropertiesAdaptor,
    ManagedObjectAdaptor {
    private val cleaner = createCleaner(obj) {
        it.release()
    }

    override fun noArgNoReturn() {
    }

    override fun getInt(): Int = INT32_VALUE

    override fun getTuple(): Pair<UInt, String> = Pair(UINT32_VALUE, STRING_VALUE)

    override fun multiply(a: Long, b: Double): Double = a * b

    override fun multiplyWithNoReply(a: Long, b: Double) {
        m_multiplyResult = a * b
        m_wasMultiplyCalled.value = true
    }

    override fun getInts16FromStruct(arg0: IntStruct): List<Short> = buildList {
        add(arg0.s)
        addAll(arg0.values)
    }

    override fun processVariant(variant: Variant): Variant {
        val value = variant.get<Double>()
        return Variant(value.toInt())
    }

    override fun getMapOfVariants(x: List<Int>, y: Pair<Variant, Variant>): Map<Int, Variant> =
        x.associateWith {
            if (it <= 0) y.first else y.second
        }

    override fun getStructInStruct(): StructOfStruct =
        StructOfStruct(STRING_VALUE, StructMap(mapOf(INT32_VALUE to INT32_VALUE)))

    override fun sumStructItems(arg0: Pair<UByte, UShort>, arg1: Pair<Int, Long>): Int =
        ((arg0.first + arg0.second).toInt() + (arg1.first.toLong() + arg1.second)).toInt()

    override fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt =
        (arg0.sum() + arg1.sum()).toUInt()

    override fun doOperation(param: UInt): UInt {
        usleep(param * 1000u)

        m_methodCallMsg = obj.getCurrentlyProcessedMessage()
        m_methodName = m_methodCallMsg!!.getMemberName()?.let(::MemberName)

        return param
    }

    override suspend fun doOperationAsync(param: UInt): UInt {
        m_methodCallMsg = obj.getCurrentlyProcessedMessage()
        m_methodName = m_methodCallMsg!!.getMemberName()?.let(::MemberName)

        if (param == 0u) {
            // Don't sleep and return the result from this thread
            return param
        } else {
            // Process asynchronously in another thread and return the result from there
            usleep(param.toInt().milliseconds.inWholeMicroseconds.toUInt())
            return param
        }
    }

    override fun getSignature(): Signature = SIGNATURE_VALUE

    override fun getObjPath(): ObjectPath = OBJECT_PATH_VALUE

    override fun getUnixFd(): UnixFd = UnixFd(UNIX_FD_VALUE)

    override fun getComplex(): Map<ULong, ComplexMapValue> = mapOf(
        0u.toULong() to ComplexMapValue(
            mapOf(
                23u.toUByte() to listOf(
                    ComplexStruct(
                        ObjectPath("/object/path"),
                        false,
                        Variant(3.14),
                        mapOf(0 to "zero")
                    )
                )
            ),
            Signature("a{t(a{ya(obva{is})}gs)}"),
            ""
        )
    )

    override fun throwError() {
        m_wasThrowErrorCalled.value = true
        throw createError(1, "A test error occurred")
    }

    override fun throwErrorWithNoReply() {
        throwError()
    }

    override fun doPrivilegedStuff() {
        // Intentionally left blank
    }

    override fun emitTwoSimpleSignals() {
        emitSimpleSignal()
        emitSignalWithMap(emptyMap())
    }

    override fun state(): String = m_state

    override fun action(): UInt = m_action

    override fun action(value: UInt) {
        m_action = value
    }

    override fun blocking(): Boolean = m_blocking

    override fun blocking(value: Boolean) {
        m_propertySetMsg = obj.getCurrentlyProcessedMessage()
        m_propertySetSender = m_propertySetMsg!!.getSender()

        m_blocking = value
    }

    private val m_state = DEFAULT_STATE_VALUE
    private var m_action = DEFAULT_ACTION_VALUE
    private var m_blocking = DEFAULT_BLOCKING_VALUE

    // For dont-expect-reply method call verifications
    public var m_wasMultiplyCalled = atomic(false)
    public var m_multiplyResult = 0.0
    public var m_wasThrowErrorCalled = atomic(false)

    public var m_methodCallMsg: Message? = null
    public var m_methodName: MethodName? = null
    public var m_propertySetMsg: Message? = null
    public var m_propertySetSender: String? = null
}

