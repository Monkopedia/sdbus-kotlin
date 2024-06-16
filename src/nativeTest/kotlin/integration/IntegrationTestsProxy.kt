@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.IProxy
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PeerProxy
import com.monkopedia.sdbus.header.PropertiesProxy
import com.monkopedia.sdbus.header.Proxy
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.UnixFd
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.callMethod
import com.monkopedia.sdbus.header.getProperty
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.header.setProperty
import com.monkopedia.sdbus.header.uponSignal
import com.monkopedia.sdbus.internal.Scope
import com.monkopedia.sdbus.internal.Unowned
import kotlinx.cinterop.Arena
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

@OptIn(ExperimentalForeignApi::class)
abstract class IntegrationTestsProxy(initialScope: DeferScope, proxy: Unowned<Proxy>) :
    Scope(initialScope), PropertiesProxy, PeerProxy {
    override val m_proxy: IProxy = proxy.own(scope)
    private var arena = Arena()

    override fun onScopeCleared() {
        arena.clear()
        kotlin.runCatching {
            m_proxy.unregister()
        }
    }

    fun getProxy(): IProxy = m_proxy

    fun registerProxy() {
        m_proxy.uponSignal("simpleSignal").onInterface(INTERFACE_NAME)
            .call({ call { -> onSimpleSignal(); } }, return_slot).own(arena)
        m_proxy.uponSignal("signalWithMap").onInterface(INTERFACE_NAME)
            .call { call { aMap: Map<Int, String> -> onSignalWithMap(aMap); } };
        m_proxy.uponSignal("signalWithVariant").onInterface(INTERFACE_NAME)
            .call { call { aVariant: Variant -> onSignalWithVariant(aVariant) } };
        registerPropertiesProxy()
    }

    abstract fun onSimpleSignal()
    abstract fun onSignalWithMap(aMap: Map<Int, String>)
    abstract fun onSignalWithVariant(aVariant: Variant)

    fun noArgNoReturn() = memScoped {
        m_proxy.callMethod("noArgNoReturn").own(this).onInterface(INTERFACE_NAME);
    }

    fun getInt(): Int = memScoped {
        return m_proxy.callMethod("getInt").own(this).onInterface(INTERFACE_NAME).readResult<Int>()
    }

    fun getTuple(): Pair<UInt, String> = memScoped {
        return m_proxy.callMethod("getTuple").own(this).onInterface(INTERFACE_NAME).readResult()
    }

    fun multiply(a: Long, b: Double): Double = memScoped {
        return m_proxy.callMethod("multiply").own(this).onInterface(INTERFACE_NAME)
            .withArguments { call(a, b) }
            .readResult<Double>();
    }

    fun multiplyWithNoReply(a: Long, b: Double) = memScoped {
        m_proxy.callMethod("multiplyWithNoReply").own(this).onInterface(INTERFACE_NAME)
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
        return m_proxy.callMethod("processVariant").own(this).onInterface(INTERFACE_NAME)
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

    fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt = memScoped {
        m_proxy.callMethod("sumArrayItems").own(this).onInterface(INTERFACE_NAME)
            .withArguments { call(arg0, arg1) }
            .readResult<UInt>();
    }

    fun doOperation(arg0: UInt): UInt = memScoped {
        m_proxy.callMethod("doOperation").own(this).onInterface(INTERFACE_NAME)
            .withArguments { call(arg0) }
            .readResult()
    }

    fun doOperationAsync(arg0: UInt): UInt = memScoped {
        m_proxy.callMethod("doOperationAsync").own(this).onInterface(INTERFACE_NAME)
            .withArguments { call(arg0) }
            .readResult()
    }

    fun getSignature(): Signature = memScoped {
        m_proxy.callMethod("getSignature").own(this).onInterface(INTERFACE_NAME).readResult()
    }

    fun getObjPath(): ObjectPath = memScoped {
        m_proxy.callMethod("getObjPath").own(this).onInterface(INTERFACE_NAME)
            .readResult();
    }

    fun getUnixFd(): UnixFd = memScoped {
        m_proxy.callMethod("getUnixFd").own(this).onInterface(INTERFACE_NAME).readResult()
    }

//    std::map<uint64_t, sdbus::Struct<std::map<uint8_t, std::vector<sdbus::Struct<sdbus::ObjectPath, bool, sdbus::Variant, std::unordered_map<int32_t, std::string>>>>, sdbus::Signature, std::string>> getComplex()
//    {
//        std::map < uint64_t, sdbus::Struct<std::map<uint8_t, std::vector<sdbus::Struct<sdbus::ObjectPath, bool, sdbus::Variant, std::unordered_map<int32_t, std::string>>>>, sdbus::Signature, std::string>> result;
//        m_proxy.callMethod("getComplex").onInterface(INTERFACE_NAME).storeResultsTo(result);
//        return result;
//    }

    fun throwError() = memScoped {
        m_proxy.callMethod("throwError").own(this).onInterface(INTERFACE_NAME).withArguments { call() };
    }

    fun throwErrorWithNoReply() = memScoped {
        m_proxy.callMethod("throwErrorWithNoReply").own(this).onInterface(INTERFACE_NAME)
            .dontExpectReply();
    }

    fun doPrivilegedStuff() = memScoped {
        m_proxy.callMethod("doPrivilegedStuff").own(this).onInterface(INTERFACE_NAME);
    }

    fun emitTwoSimpleSignals() = memScoped {
        m_proxy.callMethod("emitTwoSimpleSignals").own(this).onInterface(INTERFACE_NAME);
    }

    fun unregisterSimpleSignalHandler() {
        arena.clear();
        arena = Arena()
    }

    fun reRegisterSimpleSignalHandler() {
        m_proxy.uponSignal("simpleSignal").onInterface(INTERFACE_NAME)
            .call({ call { -> onSimpleSignal(); } }, return_slot).own(arena)
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
