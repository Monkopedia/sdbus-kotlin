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
@file:Suppress("ktlint:standard:function-literal")

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.interfaceFlags
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable

abstract class IntegrationTestsAdaptor(val obj: Object) {

    open fun registerAdaptor() {
        obj.addVTable(INTERFACE_NAME) {
            interfaceFlags {
                isDeprecated = true
                +EMITS_NO_SIGNAL
            }
            method(MethodName("noArgNoReturn")) { call { -> noArgNoReturn() } }
            method(MethodName("getInt")) {
                outputParamNames = listOf("anInt")
                call { -> getInt() }
            }
            method(MethodName("getTuple")) {
                outputParamNames = listOf("arg0", "arg1")
                call { -> getTuple() }
            }
            method(MethodName("multiply")) {
                inputParamNames = listOf("a", "b")
                outputParamNames = listOf("result")
                call { a: Long, b: Double ->
                    multiply(a, b)
                }
            }
            method(MethodName("multiplyWithNoReply")) {
                inputParamNames = listOf("a", "b")
                isDeprecated = true
                hasNoReply = true
                call { a: Long, b: Double ->
                    multiplyWithNoReply(a, b)
                }
            }
            method(MethodName("processVariant")) {
                inputParamNames = listOf("variant")
                outputParamNames = listOf("result")
                call { variant: Variant ->
                    processVariant(variant)
                }
            }
            method(MethodName("sumArrayItems")) {
                inputParamNames = listOf("arg0", "arg1")
                outputParamNames = listOf("arg0")
                call { arg0: List<UShort>, arg1: Array<ULong> ->
                    sumArrayItems(arg0, arg1)
                }
            }
            method(MethodName("doOperation")) {
                inputParamNames = listOf("arg0")
                outputParamNames = listOf("arg0")
                call { arg0: UInt ->
                    doOperation(arg0)
                }
            }
            method(MethodName("doOperationAsync")) {
                inputParamNames = listOf("arg0")
                outputParamNames = listOf("arg0")
                implementedAs(
                    acall { arg0: UInt ->
                        doOperationAsync(arg0)
                    } withContext Dispatchers.Unconfined
                )
            }
            method(MethodName("getSignature")) {
                outputParamNames = listOf("arg0")
                call { -> getSignature() }
            }
            method(MethodName("getObjPath")) {
                outputParamNames = listOf("arg0")
                call { -> getObjPath() }
            }
            method(MethodName("getUnixFd")) {
                outputParamNames = listOf("arg0")
                call { -> getUnixFd() }
            }
            method(MethodName("throwError")) {
                call { -> throwError() }
            }
            method(MethodName("throwErrorWithNoReply")) {
                hasNoReply = true
                call { -> throwErrorWithNoReply() }
            }
            method(MethodName("doPrivilegedStuff")) {
                isPrivileged = true
                call { -> doPrivilegedStuff() }
            }
            method(MethodName("emitTwoSimpleSignals")) {
                call { -> emitTwoSimpleSignals() }
            }
            signal(SignalName("simpleSignal")) {
                isDeprecated = true
            }
            signal(SignalName("signalWithMap")) { with<Map<Int, String>>("aMap") }
            signal(SignalName("signalWithVariant")) { with<Variant>("aVariant") }
            prop(PropertyName("action")) {
                withGetter { action() }
                withSetter { value: UInt ->
                    action(value)
                }
                +EMITS_INVALIDATION_SIGNAL
            }
            prop(PropertyName("blocking")) {
                withGetter { blocking() }
                withSetter { value: Boolean ->
                    blocking(value)
                }
            }
            prop(PropertyName("state")) {
                withGetter { state() }
                isDeprecated = true
                +CONST_PROPERTY_VALUE
            }

            method(MethodName("getMapOfVariants")) {
                inputParamNames = listOf("x", "y")
                outputParamNames = listOf("aMapOfVariants")
                call { x: List<Int>, y: Pair<Variant, Variant> ->
                    getMapOfVariants(x, y)
                }
            }
            method(MethodName("getStructInStruct")) {
                outputParamNames = listOf("aMapOfVariants")
                call { -> getStructInStruct() }
            }
            method(MethodName("sumStructItems")) {
                inputParamNames = listOf("arg0", "arg1")
                outputParamNames = listOf("arg0")
                call { arg0: Pair<UByte, UShort>, arg1: Pair<Int, Long> ->
                    sumStructItems(arg0, arg1)
                }
            }
            method(MethodName("getComplex")) {
                outputParamNames = listOf("arg0")
                isDeprecated = true
                call { -> getComplex() }
            }
            method(MethodName("getInts16FromStruct")) {
                inputParamNames = listOf("arg0")
                outputParamNames = listOf("arg0")
                call { arg0: IntStruct ->
                    getInts16FromStruct(arg0)
                }
            }
        }
    }

    fun emitSimpleSignal() = obj.emitSignal(INTERFACE_NAME, SignalName("simpleSignal")) { call() }

    fun emitSignalWithMap(aMap: Map<Int, String>) =
        obj.emitSignal(INTERFACE_NAME, SignalName("signalWithMap")) { call(aMap) }

    fun emitSignalWithVariant(aVariant: Variant) =
        obj.emitSignal(INTERFACE_NAME, SignalName("signalWithVariant")) { call(aVariant) }

    protected abstract fun noArgNoReturn(): Unit
    protected abstract fun getInt(): Int
    protected abstract fun getTuple(): Pair<UInt, String>
    protected abstract fun multiply(a: Long, b: Double): Double
    protected abstract fun multiplyWithNoReply(a: Long, b: Double): Unit

    @Serializable
    data class IntStruct(
        val b: UByte,
        val s: Short,
        val d: Double,
        val str: String,
        val values: List<Short>
    )

    protected abstract fun getInts16FromStruct(arg0: IntStruct): List<Short>
    protected abstract fun processVariant(variant: Variant): Variant

    @Serializable
    data class StructMap(val first: Map<Int, Int>)

    @Serializable
    data class StructOfStruct(val first: String, val second: StructMap)

    protected abstract fun getMapOfVariants(
        x: List<Int>,
        y: Pair<Variant, Variant>
    ): Map<Int, Variant>

    protected abstract fun getStructInStruct(): StructOfStruct
    protected abstract fun sumStructItems(arg0: Pair<UByte, UShort>, arg1: Pair<Int, Long>): Int
    protected abstract fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt
    protected abstract fun doOperation(arg0: UInt): UInt
    protected abstract suspend fun doOperationAsync(arg0: UInt): UInt
    protected abstract fun getSignature(): Signature
    protected abstract fun getObjPath(): ObjectPath
    protected abstract fun getUnixFd(): UnixFd

    @Serializable
    data class ComplexStruct(
        val objectPath: ObjectPath,
        val bool: Boolean,
        val variant: Variant,
        val map: Map<Int, String>
    )

    @Serializable
    data class ComplexMapValue(
        val map: Map<UByte, List<ComplexStruct>>,
        val signature: Signature,
        val str: String
    )

    protected abstract fun getComplex(): Map<ULong, ComplexMapValue>
    protected abstract fun throwError(): Unit
    protected abstract fun throwErrorWithNoReply(): Unit
    protected abstract fun doPrivilegedStuff(): Unit
    protected abstract fun emitTwoSimpleSignals(): Unit

    protected abstract fun action(): UInt
    protected abstract fun action(value: UInt): Unit
    protected abstract fun blocking(): Boolean
    protected abstract fun blocking(value: Boolean): Unit
    protected abstract fun state(): String

    companion object {
        val INTERFACE_NAME = InterfaceName("org.sdbuscpp.integrationtests")
    }
}
