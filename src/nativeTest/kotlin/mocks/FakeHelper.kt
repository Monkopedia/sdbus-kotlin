@file:Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")

package com.monkopedia.sdbus.mocks

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class FakeHelper : Fake {
    override var callHandler: CallHandler = object : CallHandler {
        override fun <T> invoke(retType: KType, vararg any: Any?): T {
            error("No call handler set")
        }
    }
}

interface CallHandler {
    fun <T : Any?> invoke(retType: KType, vararg any: Any?): T
}

interface Fake {
    var callHandler: CallHandler

    companion object {
        inline fun <reified T> Fake.invoke(vararg any: Any?): T {
            return callHandler.invoke(typeOf<T>(), *any)
        }

        suspend inline fun <reified T> Fake.coInvoke(vararg any: Any?): T {
            val context = coroutineContext
            return suspendCoroutine {
                callHandler.invoke(typeOf<T>(), *any, it, context)
            }
        }
    }
}

class RecordingHandler(val baseHandler: CallHandler) : CallHandler {
    class FakeCall(
        val retClass: KType,
        val args: Array<out Any?>,
        val returnValue: Any?
    )

    val calls = mutableListOf<FakeCall>()

    override fun <T> invoke(retType: KType, vararg any: Any?): T {
        return baseHandler.invoke<T>(retType, *any).also {
            calls.add(FakeCall(retType, any, it))
        }
    }

    companion object {
        inline fun <T : Fake> T.record(exec: (T) -> Unit): List<FakeCall> {
            val handler = RecordingHandler(callHandler)
            callHandler = handler
            try {
                exec(this)
            } finally {
                callHandler = handler.baseHandler
            }
            return handler.calls
        }
    }
}

inline fun always(value: Any?): CallHandler {
    return object : CallHandler {
        override fun <T> invoke(retType: KType, vararg any: Any?): T {
            return value as T
        }
    }
}

typealias MappingMatcher = (KType, Array<out Any?>) -> Boolean

class MappingHandler(val fallback: CallHandler) : CallHandler {
    val map = mutableListOf<Pair<MappingMatcher, CallHandler>>()

    override fun <T> invoke(retType: KType, vararg any: Any?): T {
        val match = map.firstOrNull { (matcher, _) -> matcher(retType, any) }?.second ?: fallback
        println("Handling ${any.toList()} $match ${match::class}")
        return match.invoke(retType, *any)
    }

    inline infix fun MappingMatcher.returns(value: Any?) {
        map.add(this to always(value))
    }

    inline infix fun MappingMatcher.answers(crossinline answer: (KType, Array<out Any?>) -> Any?) {
        map.add(
            this to object : CallHandler {
                override fun <T> invoke(retType: KType, vararg any: Any?): T {
                    return answer(retType, any) as T
                }
            }
        )
    }

    inline infix fun MappingMatcher.coAnswers(
        crossinline answer: suspend (KType, Array<out Any?>) -> Any?
    ) {
        map.add(
            this to object : CallHandler {
                override fun <T> invoke(retType: KType, vararg any: Any?): T {
                    println("Got args ${any.toList()}")
                    val continuation = any[any.size - 2] as Continuation<Any?>
                    val context = any[any.size - 1] as CoroutineContext
                    val scope = CoroutineScope(context)
                    val subArgs = any.toList().subList(0, any.size - 2).toTypedArray()
                    scope.launch {
                        continuation.resumeWith(runCatching { answer(retType, subArgs) })
                    }
                    return Unit as T
                }
            }
        )
    }

    inline fun method(method: KFunction<*>): MappingMatcher {
        val methodName = method.name
        return { type, args ->
            println(
                "Matching ${args.toList().map { (it as? Array<*>)?.toList() ?: it }} $methodName"
            )
            args[0] == methodName
        }
    }

    companion object {
        inline fun <T : Fake> T.configure(exec: MappingHandler.() -> Unit): T {
            val handler = MappingHandler(callHandler)
            callHandler = handler
            exec(handler)
            return this
        }
    }
}

object DefaultResponses : CallHandler {
    override fun <T> invoke(retType: KType, vararg any: Any?): T {
        return defaultValue(retType) as T
    }

    fun defaultValue(retType: KType): Any? {
        if (retType.isMarkedNullable) return null
        return when (retType.classifier) {
            Flow::class -> emptyFlow<Any>()
            List::class -> emptyList<Any>()
            Array::class -> emptyArray<Any>()
            Map::class -> emptyMap<Any, Any>()
            Set::class -> emptySet<Any>()
            Result::class -> Result.runCatching { defaultValue(retType.arguments.first().type!!) }
            Float::class -> 0.0f
            Double::class -> 0.0
            Char::class -> 0.toChar()
            Short::class -> 0.toShort()
            UShort::class -> 0.toUShort()
            Int::class -> 0
            UInt::class -> 0U
            Long::class -> 0L
            ULong::class -> 0UL
            Byte::class -> 0.toByte()
            UByte::class -> 0.toUByte()
            String::class -> ""
            else -> null
        }
    }

    fun <T : Fake> T.withDefaults(): T = apply {
        callHandler = DefaultResponses
    }
}
