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
@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.MemberName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
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

class ObjectManagerTestAdaptor(val obj: Object) {
    init {
        obj.addObjectManager()
    }

    constructor(connection: com.monkopedia.sdbus.Connection, path: ObjectPath) :
        this(createObject(connection, path))
}

class TestAdaptor(connection: com.monkopedia.sdbus.Connection, path: ObjectPath) :
    IntegrationTestsAdaptor(createObject(connection, path)) {
    private val cleaner = createCleaner(obj) {
        it.release()
    }

    override fun noArgNoReturn() {
    }

    override fun getInt(): Int = INT32_VALUE

    override fun getTuple(): Pair<UInt, String> = Pair(UINT32_VALUE, STRING_VALUE)

    override fun multiply(a: Long, b: Double): Double = a * b

    override fun multiplyWithNoReply(a: Long, b: Double) {
        multiplyResult = a * b
        wasMultiplyCalled.value = true
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

        methodCallMsg = obj.currentlyProcessedMessage
        methodName = methodCallMsg!!.getMemberName()?.let(::MemberName)

        return param
    }

    override suspend fun doOperationAsync(param: UInt): UInt {
        methodCallMsg = obj.currentlyProcessedMessage
        methodName = methodCallMsg!!.getMemberName()?.let(::MemberName)

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
        wasThrowErrorCalled.value = true
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

    override fun state(): String = state

    override fun action(): UInt = action

    override fun action(value: UInt) {
        action = value
    }

    override fun blocking(): Boolean = blocking

    override fun blocking(value: Boolean) {
        propertySetMessage = obj.currentlyProcessedMessage
        propertySetSender = propertySetMessage!!.getSender()

        blocking = value
    }

    private val state = DEFAULT_STATE_VALUE
    private var action = DEFAULT_ACTION_VALUE
    private var blocking = DEFAULT_BLOCKING_VALUE

    // For dont-expect-reply method call verifications
    var wasMultiplyCalled = atomic(false)
    var multiplyResult = 0.0
    var wasThrowErrorCalled = atomic(false)

    var methodCallMsg: Message? = null
    var methodName: MethodName? = null
    var propertySetMessage: Message? = null
    var propertySetSender: String? = null
}
