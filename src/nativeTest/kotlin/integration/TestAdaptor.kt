@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.IConnection
import com.monkopedia.sdbus.header.IObject
import com.monkopedia.sdbus.header.ManagedObjectAdaptor
import com.monkopedia.sdbus.header.MemberName
import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.MethodName
import com.monkopedia.sdbus.header.ObjectManagerAdaptor
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PropertiesAdaptor
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.UnixFd
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.createError
import com.monkopedia.sdbus.header.createObject
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.usleep

class ObjectManagerTestAdaptor(
    override val m_object: IObject
) : ObjectManagerAdaptor {
    init {
        registerObjectManagerAdaptor()
    }
    constructor(connection: IConnection, path: ObjectPath)
    : this(createObject(connection, path))

}

class TestAdaptor(connection: IConnection, path: ObjectPath) :
    IntegrationTestsAdaptor(createObject(connection, path)), PropertiesAdaptor,
    ManagedObjectAdaptor {
//    public TestAdaptor(sdbus::IConnection& connection, sdbus::ObjectPath path);
//    public ~TestAdaptor();
        private val cleaner = createCleaner(m_object) {
            it.unregister()
    }

    override fun registerAdaptor() {
        super.registerAdaptor()
//        m_object.addObjectManager();
    }

    override fun noArgNoReturn() {
    }

    override fun getInt(): Int {
        return INT32_VALUE;
    }

    override fun getTuple(): Pair<UInt, String> {
        return Pair(UINT32_VALUE, STRING_VALUE);
    }

    override fun multiply(a: Long, b: Double): Double {
        return a * b;
    }

    override fun multiplyWithNoReply(a: Long, b: Double) {
        m_multiplyResult = a * b;
        m_wasMultiplyCalled.value = true;
    }

//    std::vector<int16_t> TestAdaptor::getInts16FromStruct(const sdbus::Struct<uint8_t, int16_t, double, std::string, std::vector<int16_t>>& x)
//    {
//        std::vector<int16_t> res{x.get<1>()};
//        auto y = std::get<std::vector<int16_t>>(x);
//        res.insert(res.end(), y.begin(), y.end());
//        return res;
//    }

    override fun processVariant(variant: Variant): Variant {
        val value = variant.get<Double>()
        return Variant(value.toInt());
    }

//    std::map<int32_t, sdbus::Variant> TestAdaptor::getMapOfVariants(const std::vector<int32_t>& x, const sdbus::Struct<sdbus::Variant, sdbus::Variant>& y)
//    {
//        std::map<int32_t, sdbus::Variant> res;
//        for (auto item : x)
//        {
//            res[item] = (item <= 0) ? std::get<0>(y) : std::get<1>(y);
//        }
//        return res;
//    }
//
//    sdbus::Struct<std::string, sdbus::Struct<std::map<int32_t, int32_t>>> TestAdaptor::getStructInStruct()
//    {
//        return sdbus::Struct{STRING_VALUE, sdbus::Struct{std::map<int32_t, int32_t>{{INT32_VALUE, INT32_VALUE}}}};
//    }
//
//    int32_t TestAdaptor::sumStructItems(const sdbus::Struct<uint8_t, uint16_t>& a, const sdbus::Struct<int32_t, int64_t>& b)
//    {
//        int32_t res{0};
//        res += std::get<0>(a) + std::get<1>(a);
//        res += std::get<0>(b) + std::get<1>(b);
//        return res;
//    }

    override fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt {
        return (arg0.sum() + arg1.sum()).toUInt();
    }

    override fun doOperation(param: UInt): UInt {
        usleep(param * 1000u)

        m_methodCallMsg = m_object.getCurrentlyProcessedMessage()
        m_methodName = m_methodCallMsg!!.getMemberName()?.let(::MemberName)

        return param;
    }

    override suspend fun doOperationAsync(param: UInt): UInt {
        m_methodCallMsg = m_object.getCurrentlyProcessedMessage()
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

    override fun getSignature(): Signature {
        return SIGNATURE_VALUE;
    }

    override fun getObjPath(): ObjectPath {
        return OBJECT_PATH_VALUE;
    }

    override fun getUnixFd(): UnixFd {
        return UnixFd(UNIX_FD_VALUE);
    }

//    std::unordered_map<uint64_t, sdbus::Struct<std::map<uint8_t, std::vector<sdbus::Struct<sdbus::ObjectPath, bool, sdbus::Variant, std::map<int32_t, std::string>>>>, sdbus::Signature, std::string>> TestAdaptor::getComplex()
//    {
//        return { // unordered_map
//            {
//                0,  // uint_64_t
//                {   // struct
//                    {   // map
//                        {
//                            23,  // uint8_t
//                            {   // vector
//                                {   // struct
//                                    sdbus::ObjectPath{"/object/path"}, // object path
//                                    false,
//                                    Variant{3.14},
//                                    {   // map
//                                        {0, "zero"}
//                                    }
//                                }
//                            }
//                        }
//                    },
//                    sdbus::Signature{"a{t(a{ya(obva{is})}gs)}"}, // signature
//                    std::string{}
//                }
//            }
//        };
//    }

    override fun throwError() {
        m_wasThrowErrorCalled.value = true;
        throw createError(1, "A test error occurred");
    }

    override fun throwErrorWithNoReply() {
        throwError();
    }

    override fun doPrivilegedStuff() {
        // Intentionally left blank
    }

    override fun emitTwoSimpleSignals() {
        emitSimpleSignal();
        emitSignalWithMap(emptyMap());
    }

    override fun state(): String {
        return m_state;
    }


    override fun action(): UInt {
        return m_action;
    }

    override fun action(value: UInt) {
        m_action = value;
    }

    override fun blocking(): Boolean {
        return m_blocking;
    }

    override fun blocking(value: Boolean) {
        m_propertySetMsg = m_object.getCurrentlyProcessedMessage()
        m_propertySetSender = m_propertySetMsg!!.getSender()

        m_blocking = value;
    }

//    fun emitSignalWithoutRegistration(const sdbus::Struct<std::string, sdbus::Struct<sdbus::Signature>>& s)
//    {
//        getObject().emitSignal("signalWithoutRegistration").onInterface(sdbus::test::INTERFACE_NAME).withArguments(s);
//    }


    private val m_state = DEFAULT_STATE_VALUE;
    private var m_action = DEFAULT_ACTION_VALUE
    private var m_blocking = DEFAULT_BLOCKING_VALUE

    // For dont-expect-reply method call verifications
    public var m_wasMultiplyCalled = atomic(false)
    public var m_multiplyResult = 0.0
    public var m_wasThrowErrorCalled = atomic(false)

    public var m_methodCallMsg: Message? = null;
    public var m_methodName: MethodName? = null;
    public var m_propertySetMsg: Message? = null;
    public var m_propertySetSender: String? = null;
};

class DummyTestAdaptor(connection: IConnection, path: ObjectPath) :
    IntegrationTestsAdaptor(createObject(connection, path)),
    PropertiesAdaptor,
    ManagedObjectAdaptor {
    override fun noArgNoReturn() = Unit

    override fun getInt(): Int = 0

    override fun getTuple(): Pair<UInt, String> = 0u to ""

    override fun multiply(a: Long, b: Double): Double = 0.0

    override fun multiplyWithNoReply(a: Long, b: Double) = Unit

    override fun processVariant(variant: Variant): Variant = Variant()

    override fun sumArrayItems(arg0: List<UShort>, arg1: Array<ULong>): UInt = 0u

    override fun doOperation(arg0: UInt): UInt = 0u

    override suspend fun doOperationAsync(arg0: UInt) = arg0

    override fun getSignature(): Signature = Signature("")

    override fun getObjPath(): ObjectPath = ObjectPath("")

    override fun getUnixFd(): UnixFd = UnixFd(0)

    override fun throwError() = Unit

    override fun throwErrorWithNoReply() = Unit

    override fun doPrivilegedStuff() = Unit

    override fun emitTwoSimpleSignals() = Unit

    override fun action(): UInt = 0u

    override fun action(value: UInt) = Unit

    override fun blocking(): Boolean = false

    override fun blocking(value: Boolean) = Unit

    override fun state(): String = ""
}


