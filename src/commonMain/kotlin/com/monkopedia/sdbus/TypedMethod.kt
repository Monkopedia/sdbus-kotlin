/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus

import com.monkopedia.sdbus.TypedMethodCall.AsyncMethodCall
import com.monkopedia.sdbus.TypedMethodCall.SyncMethodCall
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@PublishedApi
internal inline fun <reified T : Any> typed() = Typed(T::class, serializer<T>())

/**
 * A reference to a type that can be serialized within a d-bus message.
 */
data class Typed<T : Any> @PublishedApi internal constructor(
    internal val cls: KClass<T>,
    internal val type: KSerializer<T>,
    internal val signature: SdbusSig = type.descriptor.asSignature
)

internal typealias InputType = List<Typed<*>>
internal typealias OutputType = Typed<*>

@PublishedApi
internal data class TypedMethod(val inputType: InputType, val outputType: OutputType)

/**
 * The definition of a reference to a callback/method invocation.
 *
 * This contains the [method] as a declaration of the signature and serialization, as well as
 * allows triggering the callback with [invoke]
 */
sealed class TypedMethodCall<T : TypedMethodCall<T>> {

    internal abstract val method: TypedMethod
    internal abstract val isAsync: Boolean

    @PublishedApi
    internal fun invoke(
        args: Message,
        onSuccess: T.(result: Typed<Any>, Any) -> Unit = { _, _ -> }
    ) = invoke(args, onSuccess, {})

    @PublishedApi
    internal abstract fun <R> invoke(
        args: Message,
        onSuccess: T.(result: Typed<Any>, Any) -> R,
        onFailure: T.(failure: Throwable) -> R,
        onResult: T.(R) -> Unit = {}
    )

    /**
     * Specifies a specific context for an async method call to be executed when invoked.
     *
     * This method has no effect when called for a synchronous method call.
     */
    open infix fun withContext(coroutineContext: CoroutineContext) = this

    abstract fun invoke(args: Throwable)

    @PublishedApi
    internal data class SyncMethodCall(
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

    @PublishedApi
    internal data class AsyncMethodCall(
        override val method: TypedMethod,
        val handler: suspend (List<Any?>) -> Any?,
        val errorCall: (suspend (Throwable?) -> Unit)? = null,
        val coroutineContext: CoroutineContext = EmptyCoroutineContext
    ) : TypedMethodCall<AsyncMethodCall>() {
        override val isAsync: Boolean
            get() = true

        override infix fun withContext(coroutineContext: CoroutineContext) =
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

/**
 * A reference to a method argument types and their values that can be serialized within a
 * d-bus message.
 */
data class TypedArguments @PublishedApi internal constructor(
    internal val inputType: InputType,
    internal val values: List<Any>
) {
    internal operator fun plus(other: TypedArguments): TypedArguments =
        TypedArguments(inputType + other.inputType, values + other.values)

    internal operator fun plus(other: Pair<Typed<*>, Any>): TypedArguments =
        TypedArguments(inputType + other.first, values + other.second)
}

typealias TypedArgumentsBuilder = TypedArgumentsBuilderContext.() -> TypedArguments

inline fun buildArgs(builder: TypedArgumentsBuilder): TypedArguments =
    TypedArgumentsBuilderContext().builder()

/**
 * This is a builder context allowing for easy construction of a [TypedMethodCall].
 *
 * In many cases, the builder context will simply grab the result from the invocation of [call] or
 * [acall], however if something more complex is needed such as [TypedMethodCall.withContext] then
 * the result may have to be set into the builder context.
 *
 * Example simple usage:
 * ```
 * method(MethodName("Multiply")) {
 *   call(this::multiply)
 * }
 * ```
 *
 * Custom context usage:
 * ```
 * method(MethodName("Multiply")) {
 *   implementedAs(acall(this::multiply) withContext Dispatchers.IO)
 * }
 * ```
 */
open class TypedMethodBuilderContext @PublishedApi internal constructor() {

    @PublishedApi
    internal open fun createCall(
        method: TypedMethod,
        handler: (List<Any?>) -> Any?,
        errorCall: ((Throwable?) -> Unit)? = null
    ): TypedMethodCall<*> = SyncMethodCall(method, handler, errorCall)

    @PublishedApi
    internal open fun createACall(
        method: TypedMethod,
        handler: suspend (List<Any?>) -> Any?,
        errorCall: (suspend (Throwable?) -> Unit)? = null,
        coroutineContext: CoroutineContext = EmptyCoroutineContext
    ): TypedMethodCall<*> = AsyncMethodCall(method, handler, errorCall, coroutineContext)

    @PublishedApi
    internal inline fun args() = listOf<Typed<*>>()

    @PublishedApi
    internal inline fun <reified A : Any> args1() = listOf(typed<A>())

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any> args2() = listOf(typed<A>(), typed<B>())

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any, reified C : Any> args3() =
        listOf(typed<A>(), typed<B>(), typed<C>())

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any
        > args4() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>())

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any
        > args5() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>(), typed<E>())

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any
        > args6() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>(), typed<E>(), typed<F>())

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any
        > args7() =
        listOf(typed<A>(), typed<B>(), typed<C>(), typed<D>(), typed<E>(), typed<F>(), typed<G>())

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any
        > args8() =
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

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any
        > args9() =
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

    @PublishedApi
    internal inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any,
        reified J : Any
        > args10() =
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

    inline fun <reified R : Any> call(crossinline handler: () -> R): TypedMethodCall<*> =
        createCall(TypedMethod(args(), typed<R>()), handler = {
            handler()
        })

    inline fun <reified R : Any, reified A : Any> call(crossinline handler: (A) -> R) =
        createCall(TypedMethod(args1<A>(), typed<R>()), handler = { args ->
            handler(args[0] as A)
        })

    inline fun <reified R : Any, reified A : Any, reified B : Any> call(
        crossinline handler: (A, B) -> R
    ) = createCall(TypedMethod(args2<A, B>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any> call(
        crossinline handler: (A, B, C) -> R
    ) = createCall(TypedMethod(args3<A, B, C>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any
        > call(
        crossinline handler: (A, B, C, D) -> R
    ) = createCall(TypedMethod(args4<A, B, C, D>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any
        > call(
        crossinline handler: (A, B, C, D, E) -> R
    ) = createCall(TypedMethod(args5<A, B, C, D, E>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any
        > call(
        crossinline handler: (A, B, C, D, E, F) -> R
    ) = createCall(TypedMethod(args6<A, B, C, D, E, F>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E, args[5] as F)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any
        > call(
        crossinline handler: (A, B, C, D, E, F, G) -> R
    ) = createCall(TypedMethod(args7<A, B, C, D, E, F, G>(), typed<R>()), handler = { args ->
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

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any
        > call(
        crossinline handler: (A, B, C, D, E, F, G, H) -> R
    ) = createCall(
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

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any
        > call(
        crossinline handler: (A, B, C, D, E, F, G, H, I) -> R
    ) = createCall(
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

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any,
        reified J : Any
        > call(
        crossinline handler: (A, B, C, D, E, F, G, H, I, J) -> R
    ) = createCall(
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

    inline fun <reified R : Any> acall(crossinline handler: suspend () -> R): TypedMethodCall<*> =
        createACall(TypedMethod(args(), typed<R>()), handler = {
            handler()
        })

    inline fun <reified R : Any, reified A : Any> acall(crossinline handler: suspend (A) -> R) =
        createACall(TypedMethod(args1<A>(), typed<R>()), handler = { args ->
            handler(args[0] as A)
        })

    inline fun <reified R : Any, reified A : Any, reified B : Any> acall(
        crossinline handler: suspend (A, B) -> R
    ) = createACall(TypedMethod(args2<A, B>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B)
    })

    inline fun <reified R : Any, reified A : Any, reified B : Any, reified C : Any> acall(
        crossinline handler: suspend (A, B, C) -> R
    ) = createACall(TypedMethod(args3<A, B, C>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any
        > acall(
        crossinline handler: suspend (A, B, C, D) -> R
    ) = createACall(TypedMethod(args4<A, B, C, D>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any
        > acall(
        crossinline handler: suspend (A, B, C, D, E) -> R
    ) = createACall(TypedMethod(args5<A, B, C, D, E>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any
        > acall(
        crossinline handler: suspend (A, B, C, D, E, F) -> R
    ) = createACall(TypedMethod(args6<A, B, C, D, E, F>(), typed<R>()), handler = { args ->
        handler(args[0] as A, args[1] as B, args[2] as C, args[3] as D, args[4] as E, args[5] as F)
    })

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any
        > acall(
        crossinline handler: suspend (A, B, C, D, E, F, G) -> R
    ) = createACall(TypedMethod(args7<A, B, C, D, E, F, G>(), typed<R>()), handler = { args ->
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

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any
        > acall(
        crossinline handler: suspend (A, B, C, D, E, F, G, H) -> R
    ) = createACall(
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

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any
        > acall(
        crossinline handler: suspend (A, B, C, D, E, F, G, H, I) -> R
    ) = createACall(
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

    inline fun <
        reified R : Any,
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any,
        reified J : Any
        > acall(
        crossinline handler: suspend (A, B, C, D, E, F, G, H, I, J) -> R
    ) = createACall(
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

/**
 * This is a builder context allowing for easy construction of a [TypedArguments].
 *
 * This is extremely similar to [TypedMethodBuilderContext] except rather than a callback it
 * supplies the arguments.
 *
 * Example usage:
 * ```
 * val result = proxy.callMethod(INTERFACE_NAME, MethodName("Multiply")) {
 *   timeout = 500.milliseconds
 *   // Sets the call parameter types to Long, Int
 *   call(1000L, 10)
 * }
 * ```
 */
open class TypedArgumentsBuilderContext {

    open fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        TypedArguments(inputType, values)

    inline fun call(): TypedArguments = createCall(TypedMethodBuilderContext().args(), emptyList())

    inline fun <reified A : Any> call(a: A) =
        createCall(TypedMethodBuilderContext().args1<A>(), listOf(a))

    inline fun <reified A : Any, reified B : Any> call(a: A, b: B) =
        createCall(TypedMethodBuilderContext().args2<A, B>(), listOf(a, b))

    inline fun <reified A : Any, reified B : Any, reified C : Any> call(a: A, b: B, c: C) =
        createCall(TypedMethodBuilderContext().args3<A, B, C>(), listOf(a, b, c))

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> call(
        a: A,
        b: B,
        c: C,
        d: D
    ) = createCall(TypedMethodBuilderContext().args4<A, B, C, D>(), listOf(a, b, c, d))

    inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any
        > call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E
    ) = createCall(TypedMethodBuilderContext().args5<A, B, C, D, E>(), listOf(a, b, c, d, e))

    inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any
        > call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F
    ) = createCall(
        TypedMethodBuilderContext().args6<A, B, C, D, E, F>(),
        listOf(a, b, c, d, e, f)
    )

    inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any
        > call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G
    ) = createCall(
        TypedMethodBuilderContext().args7<A, B, C, D, E, F, G>(),
        listOf(a, b, c, d, e, f, g)
    )

    inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any
        > call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G,
        h: H
    ) = createCall(
        TypedMethodBuilderContext().args8<A, B, C, D, E, F, G, H>(),
        listOf(a, b, c, d, e, f, g, h)
    )

    inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any
        > call(
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F,
        g: G,
        h: H,
        i: I
    ) = createCall(
        TypedMethodBuilderContext().args9<A, B, C, D, E, F, G, H, I>(),
        listOf(a, b, c, d, e, f, g, h, i)
    )

    inline fun <
        reified A : Any,
        reified B : Any,
        reified C : Any,
        reified D : Any,
        reified E : Any,
        reified F : Any,
        reified G : Any,
        reified H : Any,
        reified I : Any,
        reified J : Any
        > call(
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
    ) = createCall(
        TypedMethodBuilderContext().args10<A, B, C, D, E, F, G, H, I, J>(),
        listOf(a, b, c, d, e, f, g, h, i, j)
    )
}
