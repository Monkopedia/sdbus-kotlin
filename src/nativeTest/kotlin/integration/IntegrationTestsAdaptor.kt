@file:Suppress("ktlint:standard:function-literal")

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.ObjectAdaptor
import com.monkopedia.sdbus.ObjectPath
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

abstract class IntegrationTestsAdaptor(override val obj: IObject) : ObjectAdaptor {

    open fun registerAdaptor() {
        obj.addVTable(INTERFACE_NAME) {
            interfaceFlags {
                isDeprecated = true
                +EMITS_NO_SIGNAL
            }
            method("noArgNoReturn") { call { -> noArgNoReturn() } }
            method("getInt") {
                outputParamNames = listOf("anInt")
                call { -> getInt() }
            }
            method("getTuple") {
                outputParamNames = listOf("arg0", "arg1")
                call { -> getTuple() }
            }
            method("multiply") {
                inputParamNames = listOf("a", "b")
                outputParamNames = listOf("result")
                call { a: Long, b: Double ->
                    multiply(a, b)
                }
            }
            method("multiplyWithNoReply") {
                inputParamNames = listOf("a", "b")
                isDeprecated = true
                hasNoReply = true
                call { a: Long, b: Double ->
                    multiplyWithNoReply(a, b)
                }
            }
            method("processVariant") {
                inputParamNames = listOf("variant")
                outputParamNames = listOf("result")
                call { variant: Variant ->
                    processVariant(variant)
                }
            }
            method("sumArrayItems") {
                inputParamNames = listOf("arg0", "arg1")
                outputParamNames = listOf("arg0")
                call { arg0: List<UShort>, arg1: Array<ULong> ->
                    sumArrayItems(arg0, arg1)
                }
            }
            method("doOperation") {
                inputParamNames = listOf("arg0")
                outputParamNames = listOf("arg0")
                call { arg0: UInt ->
                    doOperation(arg0)
                }
            }
            method("doOperationAsync") {
                inputParamNames = listOf("arg0")
                outputParamNames = listOf("arg0")
                implementedAs(
                    acall { arg0: UInt ->
                        doOperationAsync(arg0)
                    } withContext Dispatchers.Unconfined
                )
            }
            method("getSignature") {
                outputParamNames = listOf("arg0")
                call { -> getSignature() }
            }
            method("getObjPath") {
                outputParamNames = listOf("arg0")
                call { -> getObjPath() }
            }
            method("getUnixFd") {
                outputParamNames = listOf("arg0")
                call { -> getUnixFd() }
            }
            method("throwError") {
                call { -> throwError() }
            }
            method("throwErrorWithNoReply") {
                hasNoReply = true
                call { -> throwErrorWithNoReply() }
            }
            method("doPrivilegedStuff") {
                isPrivileged = true
                call { -> doPrivilegedStuff() }
            }
            method("emitTwoSimpleSignals") {
                call { -> emitTwoSimpleSignals() }
            }
            signal("simpleSignal") {
                isDeprecated = true
            }
            signal("signalWithMap") { with<Map<Int, String>>("aMap") }
            signal("signalWithVariant") { with<Variant>("aVariant") }
            prop("action") {
                withGetter { action() }
                withSetter { value: UInt ->
                    action(value)
                }
                +EMITS_INVALIDATION_SIGNAL
            }
            prop("blocking") {
                withGetter { blocking() }
                withSetter { value: Boolean ->
                    blocking(value)
                }
            }
            prop("state") {
                withGetter { state() }
                isDeprecated = true
                +CONST_PROPERTY_VALUE
            }

            method("getMapOfVariants") {
                inputParamNames = listOf("x", "y")
                outputParamNames = listOf("aMapOfVariants")
                call { x: List<Int>, y: Pair<Variant, Variant> ->
                    getMapOfVariants(x, y)
                }
            }
            method("getStructInStruct") {
                outputParamNames = listOf("aMapOfVariants")
                call { -> getStructInStruct() }
            }
            method("sumStructItems") {
                inputParamNames = listOf("arg0", "arg1")
                outputParamNames = listOf("arg0")
                call { arg0: Pair<UByte, UShort>, arg1: Pair<Int, Long> ->
                    sumStructItems(arg0, arg1)
                }
            }
            method("getComplex") {
                outputParamNames = listOf("arg0")
                isDeprecated = true
                call { -> getComplex() }
            }
            method("getInts16FromStruct") {
                inputParamNames = listOf("arg0")
                outputParamNames = listOf("arg0")
                call { arg0: IntStruct ->
                    getInts16FromStruct(arg0)
                }
            }
        }
    }

    fun emitSimpleSignal() = obj.emitSignal(INTERFACE_NAME, "simpleSignal") { call() }

    fun emitSignalWithMap(aMap: Map<Int, String>) =
        obj.emitSignal(INTERFACE_NAME, "signalWithMap") { call(aMap) }

    fun emitSignalWithVariant(aVariant: Variant) =
        obj.emitSignal(INTERFACE_NAME, "signalWithVariant") { call(aVariant) }

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
        const val INTERFACE_NAME = "org.sdbuscpp.integrationtests"
    }
}
