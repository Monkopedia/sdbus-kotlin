@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PeerProxy
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.getProperty
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.ComplexMapValue
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.IntStruct
import com.monkopedia.sdbus.integration.IntegrationTestsAdaptor.StructOfStruct
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.setProperty
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
abstract class IntegrationTestsProxy(override val proxy: Proxy) :
    PropertiesProxy,
    PeerProxy {
    private var simpleSignalHandler: Resource? = null

    fun registerProxy() {
        val thiz = WeakReference(this)
        simpleSignalHandler = proxy.onSignal(INTERFACE_NAME, SignalName("simpleSignal")) {
            call { -> thiz.get()?.onSimpleSignal() ?: Unit }
        }
        proxy.onSignal(INTERFACE_NAME, SignalName("signalWithMap")) {
            call { aMap: Map<Int, String> -> thiz.get()?.onSignalWithMap(aMap) ?: Unit }
        }
        proxy.onSignal(INTERFACE_NAME, SignalName("signalWithVariant")) {
            call { aVariant: Variant ->
                thiz.get()?.onSignalWithVariant(aVariant) ?: Unit
            }
        }
        registerPropertiesProxy()
    }

    abstract fun onSimpleSignal()
    abstract fun onSignalWithMap(aMap: Map<Int, String>)
    abstract fun onSignalWithVariant(aVariant: Variant)

    fun noArgNoReturn(): Unit = proxy.callMethod(INTERFACE_NAME, MethodName("noArgNoReturn")) {}

    fun getInt(): Int = proxy.callMethod(INTERFACE_NAME, MethodName("getInt")) {}

    fun getTuple(): Pair<UInt, String> = proxy.callMethod(INTERFACE_NAME, MethodName("getTuple")) {}

    fun multiply(a: Long, b: Double): Double =
        proxy.callMethod(INTERFACE_NAME, MethodName("multiply")) {
            call(a, b)
        }

    fun multiplyWithNoReply(a: Long, b: Double): Unit =
        proxy.callMethod(INTERFACE_NAME, MethodName("multiplyWithNoReply")) {
            dontExpectReply = true
            call(a, b)
        }

    fun getInts16FromStruct(arg0: IntStruct): List<Short> = proxy
        .callMethod(INTERFACE_NAME, MethodName("getInts16FromStruct")) { call(arg0) }

    fun processVariant(variant: Variant): Variant =
        proxy.callMethod(INTERFACE_NAME, MethodName("processVariant")) {
            call(variant)
        }

    fun getMapOfVariants(x: List<Int>, y: Pair<Variant, Variant>): Map<Int, Variant> =
        proxy.callMethod(INTERFACE_NAME, MethodName("getMapOfVariants")) { call(x, y) }

    fun getStructInStruct(): StructOfStruct =
        proxy.callMethod(INTERFACE_NAME, MethodName("getStructInStruct")) {}

    fun sumStructItems(arg0: Pair<UByte, UShort>, arg1: Pair<Int, Long>): Int =
        proxy.callMethod(INTERFACE_NAME, MethodName("sumStructItems")) { call(arg0, arg1) }

    fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt =
        proxy.callMethod(INTERFACE_NAME, MethodName("sumArrayItems")) { call(arg0, arg1) }

    fun doOperation(arg0: UInt): UInt =
        proxy.callMethod(INTERFACE_NAME, MethodName("doOperation")) { call(arg0) }

    suspend fun doOperationAsync(arg0: UInt): UInt =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("doOperationAsync")) { call(arg0) }

    fun getSignature(): Signature = proxy.callMethod(INTERFACE_NAME, MethodName("getSignature")) {}

    fun getObjPath(): ObjectPath = proxy.callMethod(INTERFACE_NAME, MethodName("getObjPath")) {}

    fun getUnixFd(): UnixFd = proxy.callMethod(INTERFACE_NAME, MethodName("getUnixFd")) {}

    fun getComplex(): Map<ULong, ComplexMapValue> =
        proxy.callMethod(INTERFACE_NAME, MethodName("getComplex")) {}

    fun throwError(): Unit = proxy.callMethod(INTERFACE_NAME, MethodName("throwError")) { call() }

    fun throwErrorWithNoReply(): Unit =
        proxy.callMethod(INTERFACE_NAME, MethodName("throwErrorWithNoReply")) {
            dontExpectReply = true
        }

    fun doPrivilegedStuff(): Unit =
        proxy.callMethod(INTERFACE_NAME, MethodName("doPrivilegedStuff")) {}

    fun emitTwoSimpleSignals(): Unit =
        proxy.callMethod(INTERFACE_NAME, MethodName("emitTwoSimpleSignals")) {}

    fun unregisterSimpleSignalHandler() {
        simpleSignalHandler?.release()
        simpleSignalHandler = null
    }

    fun reRegisterSimpleSignalHandler() {
        val thiz = WeakReference(this)
        simpleSignalHandler = proxy.onSignal(INTERFACE_NAME, SignalName("simpleSignal")) {
            call { -> thiz.get()?.onSimpleSignal() ?: Unit }
        }
    }

    fun action(): UInt = proxy.getProperty(INTERFACE_NAME, PropertyName("action"))

    fun action(value: UInt) {
        proxy.setProperty(INTERFACE_NAME, PropertyName("action"), value)
    }

    fun blocking(): Boolean = proxy.getProperty(INTERFACE_NAME, PropertyName("blocking"))

    fun blocking(value: Boolean) {
        proxy.setProperty(INTERFACE_NAME, PropertyName("blocking"), value)
    }

    fun state(): String = proxy.getProperty(INTERFACE_NAME, PropertyName("state"))

    companion object {

        val INTERFACE_NAME = InterfaceName("org.sdbuscpp.integrationtests")
    }
}
