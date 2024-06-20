@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.header.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.header.IObject
import com.monkopedia.sdbus.header.ObjectAdaptor
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.UnixFd
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.addVTable
import com.monkopedia.sdbus.header.emitSignal
import com.monkopedia.sdbus.header.registerMethod
import com.monkopedia.sdbus.header.registerProperty
import com.monkopedia.sdbus.header.registerSignal
import com.monkopedia.sdbus.header.setInterfaceFlags
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers

abstract class IntegrationTestsAdaptor(override val m_object: IObject) : ObjectAdaptor {

    open fun registerAdaptor() {
        m_object.addVTable(
            setInterfaceFlags().markAsDeprecated()
                .withPropertyUpdateBehavior(EMITS_NO_SIGNAL),
            registerMethod("noArgNoReturn").implementedAs { call { -> noArgNoReturn() } },
            registerMethod("getInt").withOutputParamNames("anInt")
                .implementedAs { call { -> getInt() } },
            registerMethod("getTuple").withOutputParamNames("arg0", "arg1")
                .implementedAs { call { -> getTuple() } },
            registerMethod("multiply").withInputParamNames("a", "b").withOutputParamNames("result")
                .implementedAs {
                    call { a: Long, b: Double ->
                        multiply(a, b)
                    }
                },
            registerMethod("multiplyWithNoReply").withInputParamNames("a", "b")
                .implementedAs {
                    call { a: Long, b: Double ->
                        multiplyWithNoReply(a, b)
                    }
                }.markAsDeprecated().withNoReply(),
            registerMethod("processVariant").withInputParamNames("variant")
                .withOutputParamNames("result").implementedAs {
                    call { variant: Variant ->
                        processVariant(variant)
                    }
                },
            registerMethod("sumArrayItems").withInputParamNames("arg0", "arg1")
                .withOutputParamNames("arg0").implementedAs {
                    call { arg0: List<UShort>, arg1: Array<ULong> ->
                        sumArrayItems(arg0, arg1)
                    }
                },
            registerMethod("doOperation").withInputParamNames("arg0").withOutputParamNames("arg0")
                .implementedAs {
                    call { arg0: UInt ->
                        doOperation(arg0)
                    }
                },
            registerMethod("doOperationAsync").withInputParamNames("arg0")
                .withOutputParamNames("arg0").implementedAs {
                    acall { arg0: UInt ->
                        doOperationAsync(arg0)
                    } withContext Dispatchers.Unconfined
                },
            registerMethod("getSignature").withOutputParamNames("arg0")
                .implementedAs { call { -> getSignature(); } },
            registerMethod("getObjPath").withOutputParamNames("arg0")
                .implementedAs { call { -> getObjPath(); } },
            registerMethod("getUnixFd").withOutputParamNames("arg0")
                .implementedAs { call { -> getUnixFd(); } },
            registerMethod("throwError").implementedAs { call { -> throwError(); } },
            registerMethod("throwErrorWithNoReply").implementedAs { call { -> throwErrorWithNoReply(); } }
                .withNoReply(),
            registerMethod("doPrivilegedStuff").implementedAs { call { -> doPrivilegedStuff(); } }
                .markAsPrivileged(),
            registerMethod("emitTwoSimpleSignals").implementedAs { call { -> emitTwoSimpleSignals(); } },
            registerSignal("simpleSignal").markAsDeprecated(),
            registerSignal("signalWithMap").withParameters<Map<Int, String>>("aMap"),
            registerSignal("signalWithVariant").withParameters<Variant>("aVariant"),
            registerProperty("action").withGetter { -> action(); }
                .withSetter { value: UInt -> action(value); }
                .withUpdateBehavior(EMITS_INVALIDATION_SIGNAL),
            registerProperty("blocking").withGetter { blocking() }
                .withSetter { value: Boolean -> blocking(value); },
            registerProperty("state").withGetter { -> state(); }
                .markAsDeprecated().withUpdateBehavior(CONST_PROPERTY_VALUE)

//            registerMethod("getMapOfVariants").withInputParamNames("x", "y")
//                .withOutputParamNames("aMapOfVariants").implementedAs { call { x: List<Int>, y}}
//                [this](const std ::vector<int32_t>& x,
//                const Struct < Variant,
//                Variant > & y
//            ) { return this->getMapOfVariants(x, y); })
//        , registerMethod("getStructInStruct").withOutputParamNames("aMapOfVariants")
//            .implementedAs([this]() { return this->getStructInStruct(); })
//        , registerMethod("sumStructItems").withInputParamNames("arg0", "arg1")
//            .withOutputParamNames("arg0").implementedAs(
//            [this](
//                const Struct < uint8_t,
//                uint16_t > & arg0,
//                const Struct < int32_t,
//                int64_t > & arg1
//            ) { return this->sumStructItems(arg0, arg1); })
//        , registerMethod("getComplex").withOutputParamNames("arg0")
//            .implementedAs{ call { -> getComplex(); }}.markAsDeprecated()
//            registerMethod("getInts16FromStruct").withInputParamNames("arg0")
//                .withOutputParamNames("arg0").implementedAs(
//                [this](
//                    const Struct < uint8_t,
//                    int16_t,
//                    double,
//                    String,
//                    List<int16_t> > & arg0
//                ) { return this->getInts16FromStruct(arg0); }),
        ).forInterface(INTERFACE_NAME);
    }

    fun emitSimpleSignal() =
        m_object.emitSignal("simpleSignal").onInterface(INTERFACE_NAME).emit { call() }

    fun emitSignalWithMap(aMap: Map<Int, String>) =
        m_object.emitSignal("signalWithMap").onInterface(INTERFACE_NAME)
            .emit { call(aMap) }

    fun emitSignalWithVariant(aVariant: Variant) =
        m_object.emitSignal("signalWithVariant").onInterface(INTERFACE_NAME)
            .emit { call(aVariant) }

    protected abstract fun noArgNoReturn(): Unit
    protected abstract fun getInt(): Int
    protected abstract fun getTuple(): Pair<UInt, String>;
    protected abstract fun multiply(a: Long, b: Double): Double
    protected abstract fun multiplyWithNoReply(a: Long, b: Double): Unit

    //        protected abstract fun List<int16_t> getInts16FromStruct(const Struct<uint8_t, int16_t, double, String, List<int16_t>>& arg0) ;
    protected abstract fun processVariant(variant: Variant): Variant

    //    protected abstract fun  getMapOfVariants(x: List<Int> , y: Struct<Variant, Variant>) : Map<Int, Variant>
//        protected abstract fun Struct<String, Struct<std::map<int32_t, int32_t>>> getStructInStruct() ;
//        protected abstract fun sumStructItems(const Struct<uint8_t, uint16_t>& arg0, const Struct<int32_t, int64_t>& arg1) : Int
    protected abstract fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt
    protected abstract fun doOperation(arg0: UInt): UInt
    protected abstract suspend fun doOperationAsync(arg0: UInt): UInt
    protected abstract fun getSignature(): Signature;
    protected abstract fun getObjPath(): ObjectPath;
    protected abstract fun getUnixFd(): UnixFd;

    //        protected abstract fun std::unordered_map<uint64_t, Struct<std::map<uint8_t, List<Struct<ObjectPath, bool, Variant, std::map<int32_t, String>>>>, sdbus::Signature, String>> getComplex() ;
    protected abstract fun throwError(): Unit
    protected abstract fun throwErrorWithNoReply(): Unit
    protected abstract fun doPrivilegedStuff(): Unit
    protected abstract fun emitTwoSimpleSignals(): Unit

    protected abstract fun action(): UInt
    protected abstract fun action(value: UInt): Unit
    protected abstract fun blocking(): Boolean;
    protected abstract fun blocking(value: Boolean): Unit
    protected abstract fun state(): String;


    companion object {

        const val INTERFACE_NAME = "org.sdbuscpp.integrationtests";
    }


}
