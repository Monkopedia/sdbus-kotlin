@file:OptIn(ExperimentalForeignApi::class, ExperimentalSerializationApi::class)

package com.monkopedia.sdbus.header

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind.OPEN
import kotlinx.serialization.descriptors.PolymorphicKind.SEALED
import kotlinx.serialization.descriptors.PrimitiveKind.BOOLEAN
import kotlinx.serialization.descriptors.PrimitiveKind.BYTE
import kotlinx.serialization.descriptors.PrimitiveKind.CHAR
import kotlinx.serialization.descriptors.PrimitiveKind.DOUBLE
import kotlinx.serialization.descriptors.PrimitiveKind.FLOAT
import kotlinx.serialization.descriptors.PrimitiveKind.INT
import kotlinx.serialization.descriptors.PrimitiveKind.LONG
import kotlinx.serialization.descriptors.PrimitiveKind.SHORT
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind.CONTEXTUAL
import kotlinx.serialization.descriptors.SerialKind.ENUM
import kotlinx.serialization.descriptors.StructureKind.CLASS
import kotlinx.serialization.descriptors.StructureKind.LIST
import kotlinx.serialization.descriptors.StructureKind.MAP
import kotlinx.serialization.descriptors.StructureKind.OBJECT
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.serializer

// Callbacks from sdbus-c++
typealias method_callback = (msg: MethodCall) -> Unit
typealias async_reply_handler = (reply: MethodReply, error: Error?) -> Unit
typealias signal_handler = (signal: Signal) -> Unit
typealias message_handler = (msg: Message) -> Unit
typealias property_set_callback = (msg: PropertySetCall) -> Unit
typealias property_get_callback = (msg: PropertyGetReply) -> Unit

// Type-erased RAII-style handle to callbacks/subscriptions registered to sdbus-c++
//using Slot = std::unique_ptr<void, std::function<void(void*)>>;

//// Tag specifying that an owning handle (so-called slot) of the logical resource shall be provided to the client
data object return_slot_t

val return_slot inline get() = return_slot_t

//// Tag denoting the assumption that the caller has already obtained message ownership
data object adopt_message_t

val adopt_message inline get() = adopt_message_t

//// Tag specifying that the proxy shall not run an event loop thread on its D-Bus connection.
//// Such proxies are typically created to carry out a simple synchronous D-Bus call(s) and then are destroyed.
data object dont_run_event_loop_thread_t

val dont_run_event_loop_thread inline get() = dont_run_event_loop_thread_t

//// Tag denoting an asynchronous call that returns std::future as a handle
data object with_future_t

val with_future inline get() = with_future_t

//// Tag denoting a call where the reply shouldn't be waited for
data object dont_expect_reply_t

val dont_expect_reply inline get() = dont_expect_reply_t

//// Helper for static assert
//template <class... _T> constexpr bool always_false = false;
//
//// Helper operator+ for concatenation of `std::array`s
//template <typename _T, std::size_t _N1, std::size_t _N2>
//constexpr std::array<_T, _N1 + _N2> operator+(std::array<_T, _N1> lhs, std::array<_T, _N2> rhs);
//
//// Template specializations for getting D-Bus signatures from C++ types
//template <typename _T>
//constexpr auto signature_of_v = signature_of<_T>::value;
//
//template <typename _T, typename _Enable>

inline fun <reified T> signature_of(): signature_of {
    return signature_of(typeOf<T>())
}

val SerialDescriptor.asSignature: signature_of
    get() = signatureOf()

private fun SerialDescriptor.signatureOf(): signature_of {
    when (serialName) {
        UnixFd.SERIAL_NAME -> return signature_of_UnixFd
        Variant.SERIAL_NAME -> return signature_of_variant
        Signature.SERIAL_NAME -> return signature_of_Signature
        ObjectPath.SERIAL_NAME -> return signature_of_ObjectPath
        "kotlin.ULong" -> return signature_of_uint64_t
        "kotlin.UInt" -> return signature_of_uint32_t
        "kotlin.UShort" -> return signature_of_uint16_t
        "kotlin.UByte" -> return signature_of_uint8_t
        "kotlin.Unit" -> return signature_of_void
    }
    return when (kind) {
        BOOLEAN -> signature_of_bool
        BYTE -> signature_of_uint8_t
        CHAR -> signature_of_uint8_t
        DOUBLE -> signature_of_double
        FLOAT -> signature_of_double
        ENUM,
        INT -> signature_of_int32_t

        LONG -> signature_of_int64_t
        SHORT -> signature_of_int16_t
        STRING -> signature_of_string
        LIST -> signature_of_list(getElementDescriptor(0).asSignature)
        MAP -> signature_of_map(
            getElementDescriptor(0).asSignature,
            getElementDescriptor(1).asSignature
        )

        CLASS,
        OBJECT -> {
            signature_of_struct(elementDescriptors.map { it.asSignature })
        }
        OPEN,
        SEALED,
        CONTEXTUAL -> error("Unsupported")
    }
}

fun signature_of(type: KType): signature_of {
    return when (type.classifier) {
        Unit::class -> signature_of_void
        Boolean::class -> signature_of_bool
        UByte::class -> signature_of_uint8_t
        Short::class -> signature_of_int16_t
        UShort::class -> signature_of_uint16_t
        Int::class -> signature_of_int32_t
        UInt::class -> signature_of_uint32_t
        Long::class -> signature_of_int64_t
        ULong::class -> signature_of_uint64_t
        Double::class -> signature_of_double
        String::class -> signature_of_string
        CharSequence::class -> signature_of_string
        BusName::class -> signature_of_string
        InterfaceName::class -> signature_of_string
        MemberName::class -> signature_of_string
        Variant::class -> signature_of_variant

        ObjectPath::class -> signature_of_ObjectPath
        Signature::class -> signature_of_Signature
        UnixFd::class -> signature_of_UnixFd
        Map::class,
        AbstractMap::class,
        HashMap::class,
        LinkedHashMap::class,
        MutableMap::class,
        AbstractMutableMap::class -> {
            val (first, second) = type.arguments
            return signature_of_map(
                signature_of(first.type ?: return signature_of_invalid),
                signature_of(second.type ?: return signature_of_invalid)
            )
        }

        Array::class,
        List::class,
        MutableList::class,
        AbstractList::class,
        AbstractMutableList::class,
        ArrayList::class -> {
            val arg = type.arguments.firstOrNull()?.type
                ?: error("List type with no argument $type - ${type.classifier}")
            return signature_of_list(signature_of(arg))
        }

        Pair::class -> {
            val arg1 = type.arguments.getOrNull(0)?.type
                ?: error("Pair type with no argument 1 $type - ${type.classifier}")
            val arg2 = type.arguments.getOrNull(1)?.type
                ?: error("Pair type with no argument 2 $type - ${type.classifier}")
            return signature_of_struct(listOf(signature_of(arg1), signature_of(arg2)))
        }

        Triple::class -> {
            val arg1 = type.arguments.getOrNull(0)?.type
                ?: error("Triple type with no argument 1 $type - ${type.classifier}")
            val arg2 = type.arguments.getOrNull(1)?.type
                ?: error("Triple type with no argument 2 $type - ${type.classifier}")
            val arg3 = type.arguments.getOrNull(2)?.type
                ?: error("Triple type with no argument 3 $type - ${type.classifier}")
            return signature_of_struct(
                listOf(signature_of(arg1), signature_of(arg2), signature_of(arg3))
            )
        }

        else -> serializer(type).descriptor.asSignature
    }
}

interface signature_of {

    val value: String
    val is_valid: Boolean
    val is_trivial_dbus_type: Boolean
}

object signature_of_invalid : signature_of {
    override val value: String
        get() = ""
    override val is_valid: Boolean
        get() = false
    override val is_trivial_dbus_type: Boolean
        get() = false

}

data class signature_of_primitive<K, N : CVariable>(
    override val value: String,
    override val is_valid: Boolean = false,
    override val is_trivial_dbus_type: Boolean = false,
    val converter: NativeTypeConverter<K, N>? = null
) : signature_of {
}

data class signature_of_struct(
    val signatures: List<signature_of>
) : signature_of {

    val contents: String
        get() = signatures.joinToString("") { it.value }
    override val value: String
        get() = "($contents)"
    override val is_valid: Boolean = true
    override val is_trivial_dbus_type: Boolean = false
}

data class signature_of_dict_entry(
    val t1: signature_of,
    val t2: signature_of
) : signature_of {
    override val value: String
        get() = "${t1.value}${t2.value}"
    override val is_valid: Boolean
        get() = true
    override val is_trivial_dbus_type: Boolean
        get() = false

}

data class signature_of_map(
    val t1: signature_of,
    val t2: signature_of
) : signature_of {
    val contents: String
        get() = "${t1.value}${t2.value}"
    val dict_sig: String
        get() = "{$contents}"
    override val value: String
        get() = "a{$contents}"
    override val is_valid: Boolean
        get() = true
    override val is_trivial_dbus_type: Boolean
        get() = false
}

data class signature_of_list(
    val element: signature_of
) : signature_of {
    override val value: String
        get() = "a${element.value}"
    override val is_valid: Boolean
        get() = true
    override val is_trivial_dbus_type: Boolean
        get() = false
}


val signature_of_void = signature_of_primitive<Unit, COpaquePointerVar>(
    "",
    is_valid = true,
    is_trivial_dbus_type = false,
);

val signature_of_bool = signature_of_primitive(
    "b",
    is_valid = true,
    is_trivial_dbus_type = true,
    BooleanTypeConverter
);

val signature_of_uint8_t = signature_of_primitive(
    "y",
    is_valid = true,
    is_trivial_dbus_type = true,
    UByteTypeConverter
);

val signature_of_int16_t = signature_of_primitive(
    "n",
    is_valid = true,
    is_trivial_dbus_type = true,
    ShortTypeConverter
);

val signature_of_uint16_t = signature_of_primitive(
    "q",
    is_valid = true,
    is_trivial_dbus_type = true,
    UShortTypeConverter
);

val signature_of_int32_t = signature_of_primitive(
    "i",
    is_valid = true,
    is_trivial_dbus_type = true,
    IntTypeConverter
);

val signature_of_uint32_t = signature_of_primitive(
    "u",
    is_valid = true,
    is_trivial_dbus_type = true,
    UIntTypeConverter
);

val signature_of_int64_t = signature_of_primitive(
    "x",
    is_valid = true,
    is_trivial_dbus_type = true,
    LongTypeConverter
);

val signature_of_uint64_t = signature_of_primitive(
    "t",
    is_valid = true,
    is_trivial_dbus_type = true,
    ULongTypeConverter
);

val signature_of_double = signature_of_primitive(
    "d",
    is_valid = true,
    is_trivial_dbus_type = true,
    DoubleTypeConverter
);

val signature_of_string = signature_of_primitive<String, CPointerVar<ByteVar>>(
    "s",
    is_valid = true,
    is_trivial_dbus_type = false,
);

val signature_of_ObjectPath = signature_of_primitive<ObjectPath, CPointerVar<ByteVar>>(
    "o",
    is_valid = true,
    is_trivial_dbus_type = false,
);

val signature_of_Signature = signature_of_primitive<Signature, CPointerVar<ByteVar>>(
    "g",
    is_valid = true,
    is_trivial_dbus_type = false,
);

val signature_of_UnixFd = signature_of_primitive<UnixFd, IntVar>(
    "h",
    is_valid = true,
    is_trivial_dbus_type = false,
)

val signature_of_variant = signature_of_primitive<Any, COpaquePointerVar>(
    "v",
    is_valid = true,
    is_trivial_dbus_type = false
)

//template <typename... Elements>
//struct signature_of<std::variant<Elements...>> : signature_of<Variant>
//{};
//
//template <>


//template <typename _Element, std::size_t _Size>
//struct signature_of<std::array<_Element, _Size>> : signature_of<std::vector<_Element>>
//{
//};
//
//#ifdef __cpp_lib_span
//template <typename _Element, std::size_t _Extent>
//struct signature_of<std::span<_Element, _Extent>> : signature_of<std::vector<_Element>>
//{
//};
//#endif
//
//template <typename _Enum>
//struct signature_of<_Enum, typename std::enable_if_t<std::is_enum_v<_Enum>>>
//: public signature_of<std::underlying_type_t<_Enum>>
//{};

//
//template <typename _Key, typename _Value, typename _Hash, typename _KeyEqual, typename _Allocator>
//struct signature_of<std::unordered_map<_Key, _Value, _Hash, _KeyEqual, _Allocator>>
//: signature_of<std::map<_Key, _Value>>
//{
//};
//
//template <typename... _Types>
//struct signature_of<std::tuple<_Types...>> // A simple concatenation of signatures of _Types
//{
//    static constexpr std::array value = (std::array<char, 0>{} + ... + signature_of_v<_Types>);
//    static constexpr bool is_valid = false;
//    static constexpr bool is_trivial_dbus_type = false;
//};
//
//// To simplify conversions of arrays to C strings
//template <typename _T, std::size_t _N>
//constexpr auto as_null_terminated(std::array<_T, _N> arr)
//{
//    return arr + std::array<_T, 1>{0};
//}
//
//// Function traits implementation inspired by (c) kennytm,
//// https://github.com/kennytm/utils/blob/master/traits.hpp
//template <typename _Type>
//struct function_traits : function_traits<decltype(&_Type::operator())>
//{};
//
//template <typename _Type>
//struct function_traits<const _Type> : function_traits<_Type>
//{};
//
//template <typename _Type>
//struct function_traits<_Type&> : function_traits<_Type>
//{};
//
//template <typename _ReturnType, typename... _Args>
//struct function_traits_base
//{
//    typedef _ReturnType result_type;
//    typedef std::tuple<_Args...> arguments_type;
//    typedef std::tuple<std::decay_t<_Args>...> decayed_arguments_type;
//
//    typedef _ReturnType function_type(_Args...);
//
//    static constexpr std::size_t arity = sizeof...(_Args);
//
////        template <size_t _Idx, typename _Enabled = void>
////        struct arg;
////
////        template <size_t _Idx>
////        struct arg<_Idx, std::enable_if_t<(_Idx < arity)>>
////        {
////            typedef std::tuple_element_t<_Idx, arguments_type> type;
////        };
////
////        template <size_t _Idx>
////        struct arg<_Idx, std::enable_if_t<!(_Idx < arity)>>
////        {
////            typedef void type;
////        };
//
//    template <size_t _Idx>
//    struct arg
//        {
//            typedef std::tuple_element_t<_Idx, std::tuple<_Args...>> type;
//        };
//
//    template <size_t _Idx>
//    using arg_t = typename arg<_Idx>::type;
//};
//
//template <typename _ReturnType, typename... _Args>
//struct function_traits<_ReturnType(_Args...)> : function_traits_base<_ReturnType, _Args...>
//{
//    static constexpr bool is_async = false;
//    static constexpr bool has_error_param = false;
//};
//
//template <typename... _Args>
//struct function_traits<void(std::optional<Error>, _Args...)> : function_traits_base<void, _Args...>
//{
//    static constexpr bool has_error_param = true;
//};
//
//template <typename... _Args, typename... _Results>
//struct function_traits<void(Result<_Results...>, _Args...)> : function_traits_base<std::tuple<_Results...>, _Args...>
//{
//    static constexpr bool is_async = true;
//    using async_result_t = Result<_Results...>;
//};
//
//template <typename... _Args, typename... _Results>
//struct function_traits<void(Result<_Results...>&&, _Args...)> : function_traits_base<std::tuple<_Results...>, _Args...>
//{
//    static constexpr bool is_async = true;
//    using async_result_t = Result<_Results...>;
//};
//
//template <typename _ReturnType, typename... _Args>
//struct function_traits<_ReturnType(*)(_Args...)> : function_traits<_ReturnType(_Args...)>
//{};
//
//template <typename _ClassType, typename _ReturnType, typename... _Args>
//struct function_traits<_ReturnType(_ClassType::*)(_Args...)> : function_traits<_ReturnType(_Args...)>
//{
//    typedef _ClassType& owner_type;
//};
//
//template <typename _ClassType, typename _ReturnType, typename... _Args>
//struct function_traits<_ReturnType(_ClassType::*)(_Args...) const> : function_traits<_ReturnType(_Args...)>
//{
//    typedef const _ClassType& owner_type;
//};
//
//template <typename _ClassType, typename _ReturnType, typename... _Args>
//struct function_traits<_ReturnType(_ClassType::*)(_Args...) volatile> : function_traits<_ReturnType(_Args...)>
//{
//    typedef volatile _ClassType& owner_type;
//};
//
//template <typename _ClassType, typename _ReturnType, typename... _Args>
//struct function_traits<_ReturnType(_ClassType::*)(_Args...) const volatile> : function_traits<_ReturnType(_Args...)>
//{
//    typedef const volatile _ClassType& owner_type;
//};
//
//template <typename FunctionType>
//struct function_traits<std::function<FunctionType>> : function_traits<FunctionType>
//{};
//
//template <class _Function>
//constexpr auto is_async_method_v = function_traits<_Function>::is_async;
//
//template <class _Function>
//constexpr auto has_error_param_v = function_traits<_Function>::has_error_param;
//
//template <typename _FunctionType>
//using function_arguments_t = typename function_traits<_FunctionType>::arguments_type;
//
//template <typename _FunctionType, size_t _Idx>
//using function_argument_t = typename function_traits<_FunctionType>::template arg_t<_Idx>;
//
//template <typename _FunctionType>
//constexpr auto function_argument_count_v = function_traits<_FunctionType>::arity;
//
//template <typename _FunctionType>
//using function_result_t = typename function_traits<_FunctionType>::result_type;
//
//template <typename _Function>
//struct tuple_of_function_input_arg_types
//{
//    typedef typename function_traits<_Function>::decayed_arguments_type type;
//};
//
//template <typename _Function>
//using tuple_of_function_input_arg_types_t = typename tuple_of_function_input_arg_types<_Function>::type;
//
//template <typename _Function>
//struct tuple_of_function_output_arg_types
//{
//    typedef typename function_traits<_Function>::result_type type;
//};
//
//template <typename _Function>
//using tuple_of_function_output_arg_types_t = typename tuple_of_function_output_arg_types<_Function>::type;
//
//template <typename _Function>
//struct signature_of_function_input_arguments : signature_of<tuple_of_function_input_arg_types_t<_Function>>
//{
//    static std::string value_as_string()
//    {
//        constexpr auto signature = as_null_terminated(signature_of_v<tuple_of_function_input_arg_types_t<_Function>>);
//        return signature.data();
//    }
//};
//
//template <typename _Function>
//inline auto signature_of_function_input_arguments_v = signature_of_function_input_arguments<_Function>::value_as_string();
//
//template <typename _Function>
//struct signature_of_function_output_arguments : signature_of<tuple_of_function_output_arg_types_t<_Function>>
//{
//    static std::string value_as_string()
//    {
//        constexpr auto signature = as_null_terminated(signature_of_v<tuple_of_function_output_arg_types_t<_Function>>);
//        return signature.data();
//    }
//};
//
//template <typename _Function>
//inline auto signature_of_function_output_arguments_v = signature_of_function_output_arguments<_Function>::value_as_string();
//
//// std::future stuff for return values of async calls
//template <typename... _Args> struct future_return
//{
//    typedef std::tuple<_Args...> type;
//};
//
//template <> struct future_return<>
//{
//    typedef void type;
//};
//
//template <typename _Type> struct future_return<_Type>
//{
//    typedef _Type type;
//};
//
//template <typename... _Args>
//using future_return_t = typename future_return<_Args...>::type;
//
//// Credit: Piotr Skotnicki (https://stackoverflow.com/a/57639506)
//template <typename, typename>
//constexpr bool is_one_of_variants_types = false;
//
//template <typename... _VariantTypes, typename _QueriedType>
//constexpr bool is_one_of_variants_types<std::variant<_VariantTypes...>, _QueriedType>
//= (std::is_same_v<_QueriedType, _VariantTypes> || ...);
//
//namespace detail
//{
//    template <class _Function, class _Tuple, typename... _Args, std::size_t... _I>
//    constexpr decltype(auto) apply_impl( _Function&& f
//    , Result<_Args...>&& r
//    , _Tuple&& t
//    , std::index_sequence<_I...> )
//    {
//        return std::forward<_Function>(f)(std::move(r), std::get<_I>(std::forward<_Tuple>(t))...);
//    }
//
//    template <class _Function, class _Tuple, std::size_t... _I>
//    decltype(auto) apply_impl( _Function&& f
//    , std::optional<Error> e
//    , _Tuple&& t
//    , std::index_sequence<_I...> )
//    {
//        return std::forward<_Function>(f)(std::move(e), std::get<_I>(std::forward<_Tuple>(t))...);
//    }
//
//    // For non-void returning functions, apply_impl simply returns function return value (a tuple of values).
//    // For void-returning functions, apply_impl returns an empty tuple.
//    template <class _Function, class _Tuple, std::size_t... _I>
//    constexpr decltype(auto) apply_impl( _Function&& f
//    , _Tuple&& t
//    , std::index_sequence<_I...> )
//    {
//        if constexpr (!std::is_void_v<function_result_t<_Function>>)
//        return std::forward<_Function>(f)(std::get<_I>(std::forward<_Tuple>(t))...);
//        else
//        return std::forward<_Function>(f)(std::get<_I>(std::forward<_Tuple>(t))...), std::tuple<>{};
//    }
//}
//
//// Convert tuple `t' of values into a list of arguments
//// and invoke function `f' with those arguments.
//template <class _Function, class _Tuple>
//constexpr decltype(auto) apply(_Function&& f, _Tuple&& t)
//{
//    return detail::apply_impl( std::forward<_Function>(f)
//        , std::forward<_Tuple>(t)
//        , std::make_index_sequence<std::tuple_size<std::decay_t<_Tuple>>::value>{} );
//}
//
//// Convert tuple `t' of values into a list of arguments
//// and invoke function `f' with those arguments.
//template <class _Function, class _Tuple, typename... _Args>
//constexpr decltype(auto) apply(_Function&& f, Result<_Args...>&& r, _Tuple&& t)
//{
//    return detail::apply_impl( std::forward<_Function>(f)
//        , std::move(r)
//        , std::forward<_Tuple>(t)
//        , std::make_index_sequence<std::tuple_size<std::decay_t<_Tuple>>::value>{} );
//}
//
//// Convert tuple `t' of values into a list of arguments
//// and invoke function `f' with those arguments.
//template <class _Function, class _Tuple>
//decltype(auto) apply(_Function&& f, std::optional<Error> e, _Tuple&& t)
//{
//    return detail::apply_impl( std::forward<_Function>(f)
//        , std::move(e)
//        , std::forward<_Tuple>(t)
//        , std::make_index_sequence<std::tuple_size<std::decay_t<_Tuple>>::value>{} );
//}
//
//// Convenient concatenation of arrays
//template <typename _T, std::size_t _N1, std::size_t _N2>
//constexpr std::array<_T, _N1 + _N2> operator+(std::array<_T, _N1> lhs, std::array<_T, _N2> rhs)
//{
//    std::array<_T, _N1 + _N2> result{};
//    std::size_t index = 0;
//
//    for (auto& el : lhs) {
//    result[index] = std::move(el);
//    ++index;
//}
//    for (auto& el : rhs) {
//    result[index] = std::move(el);
//    ++index;
//}
//
//    return result;
//}
