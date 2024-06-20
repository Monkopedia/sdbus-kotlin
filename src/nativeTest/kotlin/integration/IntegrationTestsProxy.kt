@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.IProxy
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PeerProxy
import com.monkopedia.sdbus.header.PropertiesProxy
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.UnixFd
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.callMethod
import com.monkopedia.sdbus.header.callMethodAsync
import com.monkopedia.sdbus.header.getProperty
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.header.setProperty
import com.monkopedia.sdbus.header.uponSignal
import com.monkopedia.sdbus.internal.Reference
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.ref.createCleaner
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import platform.posix.usleep

@OptIn(ExperimentalForeignApi::class)
abstract class IntegrationTestsProxy(proxy: IProxy) :
    PropertiesProxy, PeerProxy {
    private var simpleSignalHandler: Any? = null
    override val m_proxy: IProxy = proxy
//    private val cleaner = createCleaner(this) {
//    }


    fun getProxy(): IProxy = m_proxy

    fun registerProxy() {
        val thiz = WeakReference(this)
        simpleSignalHandler = m_proxy.uponSignal("simpleSignal").onInterface(INTERFACE_NAME)
            .call({ call { -> thiz.get()?.onSimpleSignal() ?: Unit } }, return_slot)
        m_proxy.uponSignal("signalWithMap").onInterface(INTERFACE_NAME)
            .call { call { aMap: Map<Int, String> -> thiz.get()?.onSignalWithMap(aMap) ?: Unit } };
        m_proxy.uponSignal("signalWithVariant").onInterface(INTERFACE_NAME)
            .call { call { aVariant: Variant -> thiz.get()?.onSignalWithVariant(aVariant) ?: Unit } };
        registerPropertiesProxy()
    }

    abstract fun onSimpleSignal()
    abstract fun onSignalWithMap(aMap: Map<Int, String>)
    abstract fun onSignalWithVariant(aVariant: Variant)

    fun noArgNoReturn() = memScoped {
        m_proxy.callMethod("noArgNoReturn").onInterface(INTERFACE_NAME).readResult<Unit>()
    }

    fun getInt(): Int = memScoped {
        return m_proxy.callMethod("getInt").onInterface(INTERFACE_NAME).readResult<Int>()
    }

    fun getTuple(): Pair<UInt, String> = memScoped {
        return m_proxy.callMethod("getTuple").onInterface(INTERFACE_NAME).readResult()
    }

    fun multiply(a: Long, b: Double): Double = memScoped {
        return m_proxy.callMethod("multiply").onInterface(INTERFACE_NAME)
            .withArguments { call(a, b) }
            .readResult<Double>();
    }

    fun multiplyWithNoReply(a: Long, b: Double) = memScoped {
        m_proxy.callMethod("multiplyWithNoReply").onInterface(INTERFACE_NAME)
            .withArguments { call(a, b) }
            .dontExpectReply();
    }

//    std::vector<int16_t> getInts16FromStruct(const sdbus::Struct<uint8_t, int16_t, double, std::string, std::vector<int16_t>>& arg0)
//    {
//        std::vector<int16_t> result;
//        m_proxy.callMethod("getInts16FromStruct").onInterface(INTERFACE_NAME).withArguments(arg0)
//            .storeResultsTo(result);
//        return result;
//    }

    fun processVariant(variant: Variant): Variant = memScoped {
        return m_proxy.callMethod("processVariant").onInterface(INTERFACE_NAME)
            .withArguments { call(variant) }
            .readResult<Variant>()
    }

//    std::variant<int32_t, double, std::string> processVariant(const std::variant<int32_t, double, std::string>& variant)
//    {
//        std::variant < int32_t, double, std::string> result;
//        m_proxy.callMethod("processVariant").onInterface(INTERFACE_NAME).withArguments(variant)
//            .storeResultsTo(result);
//        return result;
//    }

//    std::map<int32_t, sdbus::Variant> getMapOfVariants(const std::vector<int32_t>& x, const sdbus::Struct<sdbus::Variant, sdbus::Variant>& y)
//    {
//        std::map < int32_t, sdbus::Variant> result;
//        m_proxy.callMethod("getMapOfVariants").onInterface(INTERFACE_NAME).withArguments(x, y)
//            .storeResultsTo(result);
//        return result;
//    }

//    sdbus::Struct<std::string, sdbus::Struct<std::map<int32_t, int32_t>>> getStructInStruct()
//    {
//        sdbus::Struct < std::string, sdbus::Struct<std::map<int32_t, int32_t>>> result;
//        m_proxy.callMethod("getStructInStruct").onInterface(INTERFACE_NAME).storeResultsTo(result);
//        return result;
//    }

//    int32_t sumStructItems(const sdbus::Struct<uint8_t, uint16_t>& arg0, const sdbus::Struct<int32_t, int64_t>& arg1)
//    {
//        int32_t result;
//        m_proxy.callMethod("sumStructItems").onInterface(INTERFACE_NAME).withArguments(arg0, arg1)
//            .storeResultsTo(result);
//        return result;
//    }

    fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt =
        m_proxy.callMethod("sumArrayItems").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0, arg1) }
            .readResult<UInt>();

    fun doOperation(arg0: UInt): UInt =
        m_proxy.callMethod("doOperation").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0) }
            .readResult()

    suspend fun doOperationAsync(arg0: UInt): UInt =
        m_proxy.callMethodAsync("doOperationAsync").onInterface(INTERFACE_NAME)
            .withArguments { call(arg0) }
            .getResult<UInt>()

    fun getSignature(): Signature =
        m_proxy.callMethod("getSignature").onInterface(INTERFACE_NAME).readResult()

    fun getObjPath(): ObjectPath =
        m_proxy.callMethod("getObjPath").onInterface(INTERFACE_NAME)
            .readResult();

    fun getUnixFd(): UnixFd =
        m_proxy.callMethod("getUnixFd").onInterface(INTERFACE_NAME).readResult()

//    std::map<uint64_t, sdbus::Struct<std::map<uint8_t, std::vector<sdbus::Struct<sdbus::ObjectPath, bool, sdbus::Variant, std::unordered_map<int32_t, std::string>>>>, sdbus::Signature, std::string>> getComplex()
//    {
//        std::map < uint64_t, sdbus::Struct<std::map<uint8_t, std::vector<sdbus::Struct<sdbus::ObjectPath, bool, sdbus::Variant, std::unordered_map<int32_t, std::string>>>>, sdbus::Signature, std::string>> result;
//        m_proxy.callMethod("getComplex").onInterface(INTERFACE_NAME).storeResultsTo(result);
//        return result;
//    }

    fun throwError() =
        m_proxy.callMethod("throwError").onInterface(INTERFACE_NAME).withArguments { call() }
            .readResult<Unit>()

    fun throwErrorWithNoReply() =
        m_proxy.callMethod("throwErrorWithNoReply").onInterface(INTERFACE_NAME)
            .dontExpectReply();

    fun doPrivilegedStuff() =
        m_proxy.callMethod("doPrivilegedStuff").onInterface(INTERFACE_NAME).readResult<Unit>()

    fun emitTwoSimpleSignals() =
        m_proxy.callMethod("emitTwoSimpleSignals").onInterface(INTERFACE_NAME).readResult<Unit>()

    @OptIn(NativeRuntimeApi::class)
    fun unregisterSimpleSignalHandler() {
        simpleSignalHandler = null
        GC.collect()
        usleep(5000u)
    }

    fun reRegisterSimpleSignalHandler() {
        val thiz = WeakReference(this)
        simpleSignalHandler = m_proxy.uponSignal("simpleSignal").onInterface(INTERFACE_NAME)
            .call({ call { -> thiz.get()?.onSimpleSignal() ?: Unit } }, return_slot)
    }

    fun action(): UInt {
        return m_proxy.getProperty("action").onInterface(INTERFACE_NAME);
    }

    fun action(value: UInt) {
        m_proxy.setProperty("action").onInterface(INTERFACE_NAME).toValue(value);
    }

    fun blocking(): Boolean {
        return m_proxy.getProperty("blocking").onInterface(INTERFACE_NAME);
    }

    fun blocking(value: Boolean) {
        m_proxy.setProperty("blocking").onInterface(INTERFACE_NAME).toValue(value);
    }

    fun state(): String {
        return m_proxy.getProperty("state").onInterface(INTERFACE_NAME);
    }

    companion object {

        const val INTERFACE_NAME = "org.sdbuscpp.integrationtests";

    }
}
