@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PeerProxy
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.getProperty
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.ComplexMapValue
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.IntStruct
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.StructOfStruct
import com.monkopedia.sdbus.return_slot
import com.monkopedia.sdbus.setProperty
import com.monkopedia.sdbus.uponSignal
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

@OptIn(ExperimentalForeignApi::class)
abstract class IntegrationTestsProxy(override val proxy: IProxy) :
    PropertiesProxy,
    PeerProxy {
    private var simpleSignalHandler: Resource? = null

    fun registerProxy() {
        val thiz = WeakReference(this)
        simpleSignalHandler = proxy.uponSignal("simpleSignal").onInterface(INTERFACE_NAME)
            .call({ call { -> thiz.get()?.onSimpleSignal() ?: Unit } }, return_slot)
        proxy.uponSignal("signalWithMap").onInterface(INTERFACE_NAME)
            .call { call { aMap: Map<Int, String> -> thiz.get()?.onSignalWithMap(aMap) ?: Unit } }
        proxy.uponSignal("signalWithVariant").onInterface(INTERFACE_NAME)
            .call {
                call { aVariant: Variant ->
                    thiz.get()?.onSignalWithVariant(aVariant) ?: Unit
                }
            }
        registerPropertiesProxy()
    }

    abstract fun onSimpleSignal()
    abstract fun onSignalWithMap(aMap: Map<Int, String>)
    abstract fun onSignalWithVariant(aVariant: Variant)

    fun noArgNoReturn() = memScoped {
        proxy.callMethod("noArgNoReturn").onInterface(INTERFACE_NAME).readResult<Unit>()
    }

    fun getInt(): Int = memScoped {
        return proxy.callMethod("getInt").onInterface(INTERFACE_NAME).readResult<Int>()
    }

    fun getTuple(): Pair<UInt, String> = memScoped {
        return proxy.callMethod("getTuple").onInterface(INTERFACE_NAME).readResult()
    }

    fun multiply(a: Long, b: Double): Double = memScoped {
        return proxy.callMethod("multiply").onInterface(INTERFACE_NAME)
            .withArguments { call(a, b) }
            .readResult<Double>()
    }

    fun multiplyWithNoReply(a: Long, b: Double) = memScoped {
        proxy.callMethod("multiplyWithNoReply").onInterface(INTERFACE_NAME)
            .withArguments { call(a, b) }
            .dontExpectReply()
    }

    fun getInts16FromStruct(arg0: IntStruct): List<Short> = proxy
        .callMethod("getInts16FromStruct")
        .onInterface(INTERFACE_NAME)
        .withArguments { call(arg0) }
        .readResult()

    fun processVariant(variant: Variant): Variant = memScoped {
        return proxy.callMethod("processVariant").onInterface(INTERFACE_NAME)
            .withArguments { call(variant) }
            .readResult<Variant>()
    }

    fun getMapOfVariants(x: List<Int>, y: Pair<Variant, Variant>): Map<Int, Variant> =
        proxy.callMethod("getMapOfVariants").onInterface(INTERFACE_NAME)
            .withArguments { call(x, y) }
            .readResult()

    fun getStructInStruct(): StructOfStruct =
        proxy.callMethod("getStructInStruct").onInterface(INTERFACE_NAME)
            .readResult()

    fun sumStructItems(arg0: Pair<UByte, UShort>, arg1: Pair<Int, Long>): Int =
        proxy.callMethod("sumStructItems").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0, arg1) }
            .readResult()

    fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt =
        proxy.callMethod("sumArrayItems").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0, arg1) }
            .readResult<UInt>()

    fun doOperation(arg0: UInt): UInt =
        proxy.callMethod("doOperation").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0) }
            .readResult()

    suspend fun doOperationAsync(arg0: UInt): UInt =
        proxy.callMethodAsync("doOperationAsync").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0) }
            .getResult<UInt>()

    fun getSignature(): Signature =
        proxy.callMethod("getSignature").onInterface(INTERFACE_NAME).readResult()

    fun getObjPath(): ObjectPath = proxy.callMethod("getObjPath").onInterface(INTERFACE_NAME)
        .readResult()

    fun getUnixFd(): UnixFd =
        proxy.callMethod("getUnixFd").onInterface(INTERFACE_NAME).readResult()

    fun getComplex(): Map<ULong, ComplexMapValue> =
        proxy.callMethod("getComplex").onInterface(INTERFACE_NAME).readResult()

    fun throwError() =
        proxy.callMethod("throwError").onInterface(INTERFACE_NAME).withArguments { call() }
            .readResult<Unit>()

    fun throwErrorWithNoReply() =
        proxy.callMethod("throwErrorWithNoReply").onInterface(INTERFACE_NAME)
            .dontExpectReply()

    fun doPrivilegedStuff() =
        proxy.callMethod("doPrivilegedStuff").onInterface(INTERFACE_NAME).readResult<Unit>()

    fun emitTwoSimpleSignals() =
        proxy.callMethod("emitTwoSimpleSignals").onInterface(INTERFACE_NAME).readResult<Unit>()

    fun unregisterSimpleSignalHandler() {
        simpleSignalHandler?.release()
        simpleSignalHandler = null
    }

    fun reRegisterSimpleSignalHandler() {
        val thiz = WeakReference(this)
        simpleSignalHandler = proxy.uponSignal("simpleSignal").onInterface(INTERFACE_NAME)
            .call({ call { -> thiz.get()?.onSimpleSignal() ?: Unit } }, return_slot)
    }

    fun action(): UInt = proxy.getProperty("action").onInterface(INTERFACE_NAME)

    fun action(value: UInt) {
        proxy.setProperty("action").onInterface(INTERFACE_NAME).toValue(value)
    }

    fun blocking(): Boolean = proxy.getProperty("blocking").onInterface(INTERFACE_NAME)

    fun blocking(value: Boolean) {
        proxy.setProperty("blocking").onInterface(INTERFACE_NAME).toValue(value)
    }

    fun state(): String = proxy.getProperty("state").onInterface(INTERFACE_NAME)

    companion object {

        const val INTERFACE_NAME = "org.sdbuscpp.integrationtests"
    }
}
