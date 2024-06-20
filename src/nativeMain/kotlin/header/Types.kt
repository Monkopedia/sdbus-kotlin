@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.monkopedia.sdbus.header

import com.monkopedia.sdbus.header.BusName.Companion.serializer
import com.monkopedia.sdbus.header.PlainMessage.Companion.createPlainMessage
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveKind.INT
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

inline fun <reified T : Any> Variant(value: T): Variant {
    val serializer = serializer<T>()
    return Variant(serializer, serializersModuleOf<T>(serializer), value)
}

inline fun <reified T> Variant.containsValueOfType(): Boolean {
    val signature = signature_of<T>()
    return signature.value == peekValueType()
}

@Serializable(Variant.Companion::class)
class Variant() {
    private val msg_: PlainMessage = createPlainMessage()

    internal constructor(message: Message) : this() {
        // Do nothing, message wil deserialize
    }

    constructor(
        strategy: SerializationStrategy<*>,
        module: SerializersModule,
        value: Any
    ) : this() {
        msg_.openVariant(strategy.descriptor.asSignature.value)
        @Suppress("UNCHECKED_CAST")
        msg_.serialize(strategy as SerializationStrategy<Any>, module, value)
        msg_.closeVariant()
        msg_.seal()
    }

    inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        return get(
            serializer,
            serializersModuleOf(serializer),
            signature_of<T>()
        )
    }

    fun <T : Any> get(
        type: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: signature_of
    ): T {
        msg_.rewind(false);

        msg_.enterVariant(signature.value);
        @Suppress("UNCHECKED_CAST")
        val v: T = msg_.deserialize(type, module)
        msg_.exitVariant();
        return v;
    }

    fun isEmpty(): Boolean {
        return msg_.isEmpty()
    }

    fun serializeTo(msg: Message) {
        msg_.rewind(true)
        msg_.copyTo(msg, true)
    }

    fun deserializeFrom(msg: Message) {
        msg.copyTo(msg_, false)
        msg_.seal()
        msg_.rewind(false)
    }

    fun peekValueType(): String? {
        msg_.rewind(false)
        val (_, contents) = msg_.peekType()
        return contents
    }

    companion object : KSerializer<Variant> {
        const val SERIAL_NAME = "sdbus.Variant"
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SERIAL_NAME)

        override fun deserialize(decoder: Decoder): Variant {
            error("Not serializable outside sdbus")
        }

        override fun serialize(encoder: Encoder, value: Variant) =
            error("Not serializable outside sdbus")
    }
}

/********************************************/
/**
 * @class Struct
 *
 * Representation of struct D-Bus type
 *
 * Struct implements tuple protocol, i.e. it's a tuple-like class.
 * It can be used with std::get<>(), std::tuple_element,
 * std::tuple_size and in structured bindings.
 *
 ***********************************************/
//template <typename... _ValueTypes>
//class Struct
//    : public std::tuple<_ValueTypes...>
//{
//    public:
//    using std::tuple<_ValueTypes...>::tuple;
//
//    Struct() = default;
//
//    explicit Struct(const std::tuple<_ValueTypes...>& t)
//    : std::tuple<_ValueTypes...>(t)
//    {
//    }
//
//    template <std::size_t _I>
//    auto& get()
//    {
//        return std::get<_I>(*this);
//    }
//
//    template <std::size_t _I>
//    const auto& get() const
//        {
//            return std::get<_I>(*this);
//        }
//};
//
//template <typename... _Elements>
//Struct(_Elements...) -> Struct<_Elements...>;
//
//template<typename... _Elements>
//constexpr Struct<std::decay_t<_Elements>...>
//make_struct(_Elements&&... args)
//{
//    typedef Struct<std::decay_t<_Elements>...> result_type;
//    return result_type(std::forward<_Elements>(args)...);
//}

/********************************************/
/**
 * @class ObjectPath
 *
 * Strong type representing the D-Bus object path
 *
 ***********************************************/
@Serializable(ObjectPath.Companion::class)
value class ObjectPath(val value: String) {
    override fun toString(): String {
        return value
    }

    companion object : KSerializer<ObjectPath> {
        const val SERIAL_NAME = "sdbus.ObjectPath"
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): ObjectPath {
            return decoder.decodeInline(descriptor).decodeString().let(::ObjectPath)
        }

        override fun serialize(encoder: Encoder, value: ObjectPath) =
            encoder.encodeInline(descriptor).encodeString(value.value)
    }
}

/********************************************/
/**
 * @class BusName
 *
 * Strong type representing the D-Bus bus/service/connection name
 *
 ***********************************************/
@Serializable(BusName.Companion::class)
value class BusName(val value: String) {
    override fun toString(): String {
        return value
    }

    companion object : KSerializer<BusName> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("sdbus.BusName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): BusName {
            return decoder.decodeInline(descriptor).decodeString().let(::BusName)
        }

        override fun serialize(encoder: Encoder, value: BusName) {
            encoder.encodeInline(descriptor).encodeString(value.value)
        }
    }
}

typealias ServiceName = BusName
typealias ConnectionName = BusName

/********************************************/
/**
 * @class InterfaceName
 *
 * Strong type representing the D-Bus interface name
 *
 ***********************************************/
@Serializable(InterfaceName.Companion::class)
value class InterfaceName(val value: String) {
    override fun toString(): String {
        return value
    }

    companion object : KSerializer<InterfaceName> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("sdbus.InterfaceName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): InterfaceName {
            return decoder.decodeInline(descriptor).decodeString().let(::InterfaceName)
        }

        override fun serialize(encoder: Encoder, value: InterfaceName) {
            encoder.encodeInline(descriptor).encodeString(value.value)
        }

    }
}

/********************************************/
/**
 * @class MemberName
 *
 * Strong type representing the D-Bus member name
 *
 ***********************************************/
@Serializable(MemberName.Companion::class)
value class MemberName(val value: String) {
    override fun toString(): String {
        return value
    }

    companion object : KSerializer<MemberName> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("sdbus.MemberName", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): MemberName {
            return decoder.decodeInline(descriptor).decodeString().let(::MemberName)
        }

        override fun serialize(encoder: Encoder, value: MemberName) {
            encoder.encodeInline(descriptor).encodeString(value.value)
        }
    }
}

typealias MethodName = MemberName
typealias SignalName = MemberName
typealias PropertyName = MemberName

/********************************************/
/**
 * @class Signature
 *
 * Strong type representing the D-Bus object path
 *
 ***********************************************/
@Serializable(Signature.Companion::class)
value class Signature(val value: String) {

    override fun toString(): String {
        return value
    }

    companion object : KSerializer<Signature> {
        const val SERIAL_NAME = "sdbus.Signature"
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Signature {
            return decoder.decodeInline(descriptor).decodeString().let(::Signature)
        }

        override fun serialize(encoder: Encoder, value: Signature) =
            encoder.encodeInline(descriptor).encodeString(value.value)
    }
}

/********************************************/
/**
 * @struct UnixFd
 *
 * UnixFd is a representation of file descriptor D-Bus type that owns
 * the underlying fd, provides access to it, and closes the fd when
 * the UnixFd goes out of scope.
 *
 * UnixFd can be default constructed (owning invalid fd), or constructed from
 * an explicitly provided fd by either duplicating or adopting that fd as-is.
 *
 ***********************************************/
@Serializable(UnixFd.Companion::class)
data class UnixFd(val fd: Int) {
    init {
        checkedDup(fd)
    }

//    ~UnixFd() {
//        close();
//    }


//    void reset(int fd = -1)
//    {
//        *this = UnixFd{fd};
//    }
//
//    void reset(int fd, adopt_fd_t)
//    {
//        *this = UnixFd{fd, adopt_fd};
//    }

//    int release()
//    {
//        return std::exchange(fd_, -1);
//    }

    val isValid: Boolean
        get() = fd >= 0

    /// Closes file descriptor, but does not set it to -1.
//    void close();

    companion object : KSerializer<UnixFd> {

        const val SERIAL_NAME = "sdbus.UnixFD"

        private fun checkedDup(fd: Int): Int {
            return fd
        }

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(SERIAL_NAME, INT)

        override fun deserialize(decoder: Decoder): UnixFd {
            return decoder.decodeInline(descriptor).decodeInt().let(::UnixFd)
        }

        override fun serialize(encoder: Encoder, value: UnixFd) {
            encoder.encodeInline(descriptor).encodeInt(value.fd)
        }
    }
};

/********************************************/
/**
 * @typedef DictEntry
 *
 * DictEntry is implemented as std::pair, a standard
 * value_type in STL(-like) associative containers.
 *
 ***********************************************/
//template<typename _T1, typename _T2>
//using DictEntry = std::pair<_T1, _T2>;
//
//}
//
//// Making sdbus::Struct implement the tuple-protocol, i.e. be a tuple-like type
//template <size_t _I, typename... _ValueTypes>
//struct std::tuple_element<_I, sdbus::Struct<_ValueTypes...>>
//: std::tuple_element<_I, std::tuple<_ValueTypes...>>
//{};
//template <typename... _ValueTypes>
//struct std::tuple_size<sdbus::Struct<_ValueTypes...>>
//: std::tuple_size<std::tuple<_ValueTypes...>>
//{};

/********************************************/
/**
 * @name SDBUSCPP_REGISTER_STRUCT
 *
 * A convenient way to extend sdbus-c++ type system with user-defined structs.
 *
 * The macro teaches sdbus-c++ to recognize the user-defined struct
 * as a valid C++ representation of a D-Bus Struct type, and enables
 * clients to use their struct conveniently instead of the (too
 * generic and less expressive) `sdbus::Struct<...>` in sdbus-c++ API.
 *
 * The first argument is the struct type name and the remaining arguments
 * are names of struct members. Members must be of types supported by
 * sdbus-c++ (or of user-defined types that sdbus-c++ was taught to support).
 * Members can be other structs (nesting is supported).
 * The macro must be placed in the global namespace.
 *
 * For example, given the user-defined struct `ABC`:
 *
 * namespace foo {
 *     struct ABC
 *     {
 *         int number;
 *         std::string name;
 *         std::vector<double> data;
 *     };
 * }
 *
 * one can teach sdbus-c++ about the contents of this struct simply with:
 *
 * SDBUSCPP_REGISTER_STRUCT(foo::ABC, number, name, data);
 *
 * The macro effectively generates the `sdbus::Message` serialization
 * and deserialization operators and the `sdbus::signature_of`
 * specialization for `foo::ABC`.
 *
 * Up to 16 struct members are supported by the macro.
 *
 ***********************************************/
//#define SDBUSCPP_REGISTER_STRUCT(STRUCT, ...)                                                                   \
//namespace sdbus {                                                                                           \
//    static_assert(SDBUSCPP_PP_NARG(__VA_ARGS__) <= 16,                                                      \
//    "Not more than 16 struct members are supported, please open an issue if you need more");   \
//    sdbus::Message& operator<<(sdbus::Message& msg, const STRUCT& items)                                    \
//    {                                                                                                       \
//        return msg << sdbus::Struct{std::forward_as_tuple(SDBUSCPP_STRUCT_MEMBERS(items, __VA_ARGS__))};    \
//    }                                                                                                       \
//    sdbus::Message& operator>>(sdbus::Message& msg, STRUCT& items)                                          \
//    {                                                                                                       \
//        sdbus::Struct s{std::forward_as_tuple(SDBUSCPP_STRUCT_MEMBERS(items, __VA_ARGS__))};                \
//        return msg >> s;                                                                                    \
//    }                                                                                                       \
//    template <>                                                                                             \
//    struct signature_of<STRUCT>                                                                             \
//    : signature_of<sdbus::Struct<SDBUSCPP_STRUCT_MEMBER_TYPES(STRUCT, __VA_ARGS__)>>                    \
//    {};                                                                                                     \
//}                                                                                                           \
/**/

/*!
 * @cond SDBUSCPP_INTERNAL
 *
 * Internal helper preprocessor facilities
 */
//#define SDBUSCPP_STRUCT_MEMBERS(STRUCT, ...)                                                                    \
//SDBUSCPP_PP_CAT(SDBUSCPP_STRUCT_MEMBERS_, SDBUSCPP_PP_NARG(__VA_ARGS__))(STRUCT, __VA_ARGS__)               \
///**/
//#define SDBUSCPP_STRUCT_MEMBERS_1(S, M1) S.M1
//#define SDBUSCPP_STRUCT_MEMBERS_2(S, M1, M2) S.M1, S.M2
//#define SDBUSCPP_STRUCT_MEMBERS_3(S, M1, M2, M3) S.M1, S.M2, S.M3
//#define SDBUSCPP_STRUCT_MEMBERS_4(S, M1, M2, M3, M4) S.M1, S.M2, S.M3, S.M4
//#define SDBUSCPP_STRUCT_MEMBERS_5(S, M1, M2, M3, M4, M5) S.M1, S.M2, S.M3, S.M4, S.M5
//#define SDBUSCPP_STRUCT_MEMBERS_6(S, M1, M2, M3, M4, M5, M6) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6
//#define SDBUSCPP_STRUCT_MEMBERS_7(S, M1, M2, M3, M4, M5, M6, M7) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7
//#define SDBUSCPP_STRUCT_MEMBERS_8(S, M1, M2, M3, M4, M5, M6, M7, M8) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8
//#define SDBUSCPP_STRUCT_MEMBERS_9(S, M1, M2, M3, M4, M5, M6, M7, M8, M9) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9
//#define SDBUSCPP_STRUCT_MEMBERS_10(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10
//#define SDBUSCPP_STRUCT_MEMBERS_11(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10, S.M11
//#define SDBUSCPP_STRUCT_MEMBERS_12(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10, S.M11, S.M12
//#define SDBUSCPP_STRUCT_MEMBERS_13(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10, S.M11, S.M12, S.M13
//#define SDBUSCPP_STRUCT_MEMBERS_14(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13, M14) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10, S.M11, S.M12, S.M13, S.M14
//#define SDBUSCPP_STRUCT_MEMBERS_15(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13, M14, M15) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10, S.M11, S.M12, S.M13, S.M14, S.M15
//#define SDBUSCPP_STRUCT_MEMBERS_16(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13, M14, M15, M16) S.M1, S.M2, S.M3, S.M4, S.M5, S.M6, S.M7, S.M8, S.M9, S.M10, S.M11, S.M12, S.M13, S.M14, S.M15, S.M16
//
//#define SDBUSCPP_STRUCT_MEMBER_TYPES(STRUCT, ...)                                                               \
//SDBUSCPP_PP_CAT(SDBUSCPP_STRUCT_MEMBER_TYPES_, SDBUSCPP_PP_NARG(__VA_ARGS__))(STRUCT, __VA_ARGS__)          \
///**/
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_1(S, M1) decltype(S::M1)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_2(S, M1, M2) decltype(S::M1), decltype(S::M2)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_3(S, M1, M2, M3) decltype(S::M1), decltype(S::M2), decltype(S::M3)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_4(S, M1, M2, M3, M4) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_5(S, M1, M2, M3, M4, M5) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_6(S, M1, M2, M3, M4, M5, M6) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_7(S, M1, M2, M3, M4, M5, M6, M7) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_8(S, M1, M2, M3, M4, M5, M6, M7, M8) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_9(S, M1, M2, M3, M4, M5, M6, M7, M8, M9) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_10(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_11(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10), decltype(S::M11)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_12(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10), decltype(S::M11), decltype(S::M12)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_13(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10), decltype(S::M11), decltype(S::M12), decltype(S::M13)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_14(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13, M14) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10), decltype(S::M11), decltype(S::M12), decltype(S::M13), decltype(S::M14)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_15(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13, M14, M15) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10), decltype(S::M11), decltype(S::M12), decltype(S::M13), decltype(S::M14), decltype(S::M15)
//#define SDBUSCPP_STRUCT_MEMBER_TYPES_16(S, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12, M13, M14, M15, M16) decltype(S::M1), decltype(S::M2), decltype(S::M3), decltype(S::M4), decltype(S::M5), decltype(S::M6), decltype(S::M7), decltype(S::M8), decltype(S::M9), decltype(S::M10), decltype(S::M11), decltype(S::M12), decltype(S::M13), decltype(S::M14), decltype(S::M15), decltype(S::M16)
//
//#define SDBUSCPP_PP_CAT(X, Y) SDBUSCPP_PP_CAT_IMPL(X, Y)
//#define SDBUSCPP_PP_CAT_IMPL(X, Y) X##Y
//#define SDBUSCPP_PP_NARG(...) SDBUSCPP_PP_NARG_IMPL(__VA_ARGS__, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
//#define SDBUSCPP_PP_NARG_IMPL(_1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, _20, _21, _22, _23, _24, _25, _26, _27, _28, _29, _30, _31, _32, _N, ...) _N
