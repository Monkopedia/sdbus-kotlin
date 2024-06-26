package com.monkopedia.sdbus

import com.monkopedia.sdbus.TypedMethodBuilderContext.args
import com.monkopedia.sdbus.TypedMethodBuilderContext.args1
import com.monkopedia.sdbus.TypedMethodBuilderContext.args10
import com.monkopedia.sdbus.TypedMethodBuilderContext.args2
import com.monkopedia.sdbus.TypedMethodBuilderContext.args3
import com.monkopedia.sdbus.TypedMethodBuilderContext.args4
import com.monkopedia.sdbus.TypedMethodBuilderContext.args5
import com.monkopedia.sdbus.TypedMethodBuilderContext.args6
import com.monkopedia.sdbus.TypedMethodBuilderContext.args7
import com.monkopedia.sdbus.TypedMethodBuilderContext.args8
import com.monkopedia.sdbus.TypedMethodBuilderContext.args9
import com.monkopedia.sdbus.TypedMethodCall.AsyncMethodCall
import com.monkopedia.sdbus.TypedMethodCall.SyncMethodCall
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

inline fun <reified T : Any> typed() = Typed(T::class, serializer<T>())

data class Typed<T : Any>(
    val cls: KClass<T>,
    val type: KSerializer<T>,
    val signature: SdbusSig = type.descriptor.asSignature
)

typealias InputType = List<Typed<*>>
typealias OutputType = Typed<*>

data class TypedMethod(val inputType: InputType, val outputType: OutputType)

sealed class TypedMethodCall<T : TypedMethodCall<T>> {

    abstract val method: TypedMethod
    abstract val isAsync: Boolean

    fun invoke(args: Message, onSuccess: T.(result: Typed<Any>, Any) -> Unit = { _, _ -> }) =
        invoke(args, onSuccess, {})

    abstract fun <R> invoke(
        args: Message,
        onSuccess: T.(result: Typed<Any>, Any) -> R,
        onFailure: T.(failure: Throwable) -> R,
        onResult: T.(R) -> Unit = {}
    )

    abstract fun invoke(args: Throwable)

    data class SyncMethodCall(
        override val method: TypedMethod,
        val handler: (List<Any?>) -> Any?,
        val errorCall: ((Throwable?) -> Unit)? = null
    ) : TypedMethodCall<SyncMethodCall>() {
        override val isAsync: Boolean
            get() = false

        override fun <R> invoke(
            args: Message,
            onSuccess: SyncMethodCall.(result: Typed<Any>, Any) -> R,
            onFailure: SyncMethodCall.(failure: Throwable) -> R,
            onResult: SyncMethodCall.(R) -> Unit
        ) {
            runCatching {
                val realArgs = args.deserialize(method)
                handler(realArgs)!!
            }.fold(
                onSuccess = {
                    @Suppress("UNCHECKED_CAST")
                    onSuccess(method.outputType as Typed<Any>, it)
                },
                onFailure = {
                    errorCall?.invoke(it)
                    onFailure(it)
                }
            ).let { onResult(it) }
        }

        override fun invoke(args: Throwable) {
            errorCall?.invoke(args)
        }
    }

    data class AsyncMethodCall(
        override val method: TypedMethod,
        val handler: suspend (List<Any?>) -> Any?,
        val errorCall: (suspend (Throwable?) -> Unit)? = null,
        val coroutineContext: CoroutineContext = EmptyCoroutineContext
    ) : TypedMethodCall<AsyncMethodCall>() {
        override val isAsync: Boolean
            get() = true

        infix fun withContext(coroutineContext: CoroutineContext) =
            copy(coroutineContext = coroutineContext)

        override fun <R> invoke(
            args: Message,
            onSuccess: AsyncMethodCall.(result: Typed<Any>, Any) -> R,
            onFailure: AsyncMethodCall.(failure: Throwable) -> R,
            onResult: AsyncMethodCall.(R) -> Unit
        ) {
            CoroutineScope(coroutineContext).launch {
                runCatching {
                    val realArgs = args.deserialize(method)
                    handler(realArgs)!!
                }.fold(
                    onSuccess = {
                        @Suppress("UNCHECKED_CAST")
                        onSuccess(method.outputType as Typed<Any>, it)
                    },
                    onFailure = {
                        errorCall?.invoke(it)
                        onFailure(it)
                    }
                ).let { onResult(it) }
            }
        }

        override fun invoke(args: Throwable) {
            CoroutineScope(coroutineContext).launch {
                errorCall?.invoke(args)
            }
        }
    }
}

data class TypedArguments(val inputType: InputType, val values: List<Any>) {
    operator fun plus(other: TypedArguments): TypedArguments =
        TypedArguments(inputType + other.inputType, values + other.values)

    operator fun plus(other: Pair<Typed<*>, Any>): TypedArguments =
        TypedArguments(inputType + other.first, values + other.second)
}

infix fun TypedMethodCall<*>.onError(handler: (Throwable?) -> Unit) = when (this) {
    is AsyncMethodCall -> copy(errorCall = { handler(it) })
    is SyncMethodCall -> copy(errorCall = handler)
}

typealias TypedMethodBuilder = TypedMethodBuilderContext.() -> TypedMethodCall<*>
typealias TypedArgumentsBuilder = TypedArgumentsBuilderContext.() -> TypedArguments

inline fun build(builder: TypedMethodBuilder): TypedMethodCall<*> =
    TypedMethodBuilderContext.builder()

inline fun build(builder: TypedArgumentsBuilder): TypedArguments =
    TypedArgumentsBuilderContext.builder()

object TypedMethodBuilderContext {

    inline fun args() = listOf<Typed<*>>()
    inline fun <reified A : Any> args1() = listOf(typed<A>())
    inline fun <reified A : Any, reified B : Any> args2() = listOf(typed<A>(), typed<B>())
    inline fun <reified A : Any, reified B : Any, reified C : Any> args3() =
        listOf(typed<A>(), typed<B>(), typed<C>())

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> args4() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>())

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any> args5() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>(), typed<E>())

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any> args6() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>(), typed<E>(), typed<F>())

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any> args7() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>(), typed<E>(), typed<F>(), typed<G>())

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any> args8() =
        listOf(
            typed<A>(),
            typed<B>(),
            typed<C>(),
            typed<D>(),
            typed<E>(),
            typed<F>(),
            typed<G>(),
            typed<H>()
        )

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any> args9() =
        listOf(
            typed<A>(),
            typed<B>(),
            typed<C>(),
            typed<D>(),
            typed<E>(),
            typed<F>(),
            typed<G>(),
            typed<H>(),
            typed<I>()
        )

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any, reified J : Any> args10() =
        listOf(
            typed<A>(),
            typed<B>(),
            typed<C>(),
            typed<D>(),
            typed<E>(),
            typed<F>(),
            typed<G>(),
            typed<H>(),
            typed<I>(),
            typed<J>()
        )

    inline fun <reified R : Any> call(crossinline handler: () -> R): SyncMethodCall =
        SyncMethodCall(TypedMethod(args(), typed<R>()), handler = {
            handler()
        })

    inline fun <reified R : Any, reified A : Any> call(crossinline handler: (A) -> R) =
        SyncMethodCall(TypedMethod(args1<A>(), typed<R>()), handler = { args ->
            handler(args[0] as A)
        })

    inline fun <reified R : Any, reified A : Any, reified B : Any> call(
        crossinline handler: (A, B) -> R
    ) = SyncMethodCall(TypedMethod(args2<A, B>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any> call(
        crossinline handler: (A, B, C) -> R
    ) = SyncMethodCall(TypedMethod(args3<A, B, C>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any> call(
        crossinline handler: (A, B, C, D) -> R
    ) = SyncMethodCall(TypedMethod(args4<A, B, C, D>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any> call(
        crossinline handler: (A, B, C, D, E) -> R
    ) = SyncMethodCall(TypedMethod(args5<A, B, C, D, E>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any> call(
        crossinline handler: (A, B, C, D, E, F) -> R
    ) = SyncMethodCall(TypedMethod(args6<A, B, C, D, E, F>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E, args[5] as F)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any> call(
        crossinline handler: (A, B, C, D, E, F, G) -> R
    ) = SyncMethodCall(TypedMethod(args7<A, B, C, D, E, F, G>(), typed<R>()), handler = { args ->
        handler(
            args[0] as A,
            args[1] as B,
            args[2] as C,
            args[3] as D,
            args[4] as E,
            args[5] as F,
            args[6] as G
        )
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any> call(
        crossinline handler: (A, B, C, D, E, F, G, H) -> R
    ) = SyncMethodCall(
        TypedMethod(args8<A, B, C, D, E, F, G, H>(), typed<R>()),
        handler = { args ->
            handler(
                args[0] as A,
                args[1] as B,
                args[2] as C,
                args[3] as D,
                args[4] as E,
                args[5] as F,
                args[6] as G,
                args[7] as H
            )
        }
    )

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any> call(
        crossinline handler: (A, B, C, D, E, F, G, H, I) -> R
    ) = SyncMethodCall(
        TypedMethod(args9<A, B, C, D, E, F, G, H, I>(), typed<R>()),
        handler = { args ->
            handler(
                args[0] as A,
                args[1] as B,
                args[2] as C,
                args[3] as D,
                args[4] as E,
                args[5] as F,
                args[6] as G,
                args[7] as H,
                args[8] as I
            )
        }
    )

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any, reified J : Any> call(
        crossinline handler: (A, B, C, D, E, F, G, H, I, J) -> R
    ) = SyncMethodCall(
        TypedMethod(args10<A, B, C, D, E, F, G, H, I, J>(), typed<R>()),
        handler = { args ->
            handler(
                args[0] as A,
                args[1] as B,
                args[2] as C,
                args[3] as D,
                args[4] as E,
                args[5] as F,
                args[6] as G,
                args[7] as H,
                args[8] as I,
                args[9] as J
            )
        }
    )

    inline fun <reified R : Any> acall(crossinline handler: () -> R): AsyncMethodCall =
        AsyncMethodCall(TypedMethod(args(), typed<R>()), handler = {
            handler()
        })

    inline fun <reified R : Any, reified A : Any> acall(crossinline handler: suspend (A) -> R) =
        AsyncMethodCall(TypedMethod(args1<A>(), typed<R>()), handler = { args ->
            handler(args[0] as A)
        })

    inline fun <reified R : Any, reified A : Any, reified B : Any> acall(
        crossinline handler: suspend (A, B) -> R
    ) = AsyncMethodCall(TypedMethod(args2<A, B>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any> acall(
        crossinline handler: suspend (A, B, C) -> R
    ) = AsyncMethodCall(TypedMethod(args3<A, B, C>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any> acall(
        crossinline handler: suspend (A, B, C, D) -> R
    ) = AsyncMethodCall(TypedMethod(args4<A, B, C, D>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any> acall(
        crossinline handler: suspend (A, B, C, D, E) -> R
    ) = AsyncMethodCall(TypedMethod(args5<A, B, C, D, E>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any> acall(
        crossinline handler: suspend (A, B, C, D, E, F) -> R
    ) = AsyncMethodCall(TypedMethod(args6<A, B, C, D, E, F>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E, args[5] as F)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any> acall(
        crossinline handler: suspend (A, B, C, D, E, F, G) -> R
    ) = AsyncMethodCall(TypedMethod(args7<A, B, C, D, E, F, G>(), typed<R>()), handler = { args ->
        handler(
            args[0] as A,
            args[1] as B,
            args[2] as C,
            args[3] as D,
            args[4] as E,
            args[5] as F,
            args[6] as G
        )
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any> acall(
        crossinline handler: suspend (A, B, C, D, E, F, G, H) -> R
    ) = AsyncMethodCall(
        TypedMethod(args8<A, B, C, D, E, F, G, H>(), typed<R>()),
        handler = { args ->
            handler(
                args[0] as A,
                args[1] as B,
                args[2] as C,
                args[3] as D,
                args[4] as E,
                args[5] as F,
                args[6] as G,
                args[7] as H
            )
        }
    )

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any> acall(
        crossinline handler: suspend (A, B, C, D, E, F, G, H, I) -> R
    ) = AsyncMethodCall(
        TypedMethod(args9<A, B, C, D, E, F, G, H, I>(), typed<R>()),
        handler = { args ->
            handler(
                args[0] as A,
                args[1] as B,
                args[2] as C,
                args[3] as D,
                args[4] as E,
                args[5] as F,
                args[6] as G,
                args[7] as H,
                args[8] as I
            )
        }
    )

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any, reified J : Any> acall(
        crossinline handler: suspend (A, B, C, D, E, F, G, H, I, J) -> R
    ) = AsyncMethodCall(
        TypedMethod(args10<A, B, C, D, E, F, G, H, I, J>(), typed<R>()),
        handler = { args ->
            handler(
                args[0] as A,
                args[1] as B,
                args[2] as C,
                args[3] as D,
                args[4] as E,
                args[5] as F,
                args[6] as G,
                args[7] as H,
                args[8] as I,
                args[9] as J
            )
        }
    )
}

object TypedArgumentsBuilderContext {

    inline fun call(): TypedArguments = TypedArguments(args(), emptyList())

    inline fun <reified A : Any> call(a: A) = TypedArguments(args1<A>(), listOf(a))

    inline fun <reified A : Any, reified B : Any> call(a: A, b: B) =
        TypedArguments(args2<A, B>(), listOf(a, b))

    inline fun <reified A : Any, reified B : Any, reified C : Any> call(a: A, b: B, c: C) =
        TypedArguments(args3<A, B, C>(), listOf(a, b, c))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> call(
        a: A,
        b: B,
        c: C,
        d: D
    ) = TypedArguments(args4<A, B, C, D>(), listOf(a, b, c, d))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any> call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E
    ) = TypedArguments(args5<A, B, C, D, E>(), listOf(a, b, c, d, e))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any> call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F
    ) = TypedArguments(args6<A, B, C, D, E, F>(), listOf(a, b, c, d, e, f))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any> call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G
    ) = TypedArguments(args7<A, B, C, D, E, F, G>(), listOf(a, b, c, d, e, f, g))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any> call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G,
        h: H
    ) = TypedArguments(args8<A, B, C, D, E, F, G, H>(), listOf(a, b, c, d, e, f, g, h))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any> call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G,
        h: H,
        i: I
    ) = TypedArguments(args9<A, B, C, D, E, F, G, H, I>(), listOf(a, b, c, d, e, f, g, h, i))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E : Any, reified F : Any, reified G : Any, reified H : Any, reified I : Any, reified J : Any> call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G,
        h: H,
        i: I,
        j: J
    ) = TypedArguments(args10<A, B, C, D, E, F, G, H, I, J>(), listOf(a, b, c, d, e, f, g, h, i, j))
}
