@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import kotlin.reflect.KMutableProperty1
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alignOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value

interface NativeTypeConverter<K, N : CVariable> {

    fun N.set(k: K)
    fun get(n: N): K
    val size: Long
    val align: Int
    fun allocNative(scope: MemScope, source: List<K>): CArrayPointer<N>
    fun readNativeInto(array: CArrayPointer<N>, size: Int, list: MutableList<K>)
}

inline fun <reified K, reified N: CVariable> buildTypeConverter(prop: KMutableProperty1<N, K>): NativeTypeConverter<K, N> {
    return object : NativeTypeConverter<K, N> {
        override fun N.set(k: K) {
            prop.set(this, k)
        }

        override fun get(n: N): K {
            return prop.get(n)
        }

        override val size: Long
            get() = sizeOf<N>()
        override val align: Int
            get() = alignOf<N>()

        override fun readNativeInto(array: CArrayPointer<N>, size: Int, list: MutableList<K>) {
            doReadNativeInto(array, size, list)
        }

        override fun allocNative(scope: MemScope, source: List<K>): CArrayPointer<N> {
            return doAllocNative(scope, source)
        }
    }
}

val BooleanTypeConverter = buildTypeConverter(BooleanVar::value)
val ByteTypeConverter = buildTypeConverter(ByteVar::value)
val ShortTypeConverter = buildTypeConverter(ShortVar::value)
val IntTypeConverter = buildTypeConverter(IntVar::value)
val LongTypeConverter = buildTypeConverter(LongVar::value)
val UByteTypeConverter = buildTypeConverter(UByteVar::value)
val UShortTypeConverter = buildTypeConverter(UShortVar::value)
val UIntTypeConverter = buildTypeConverter(UIntVar::value)
val ULongTypeConverter = buildTypeConverter(ULongVar::value)
val FloatTypeConverter = buildTypeConverter(FloatVar::value)
val DoubleTypeConverter = buildTypeConverter(DoubleVar::value)

inline fun <K, reified N : CVariable> NativeTypeConverter<K, N>.doAllocNative(scope: MemScope, source: List<K>): CArrayPointer<N> {
    println("Alloc native ${source.size}")
    return scope.allocArray(source.size) {
        set(source[it])
    }
}

inline fun <K, reified N : CVariable> NativeTypeConverter<K, N>.doReadNativeInto(array: CArrayPointer<N>, size: Int, list: MutableList<K>) {
    for (i in 0 until size) {
        list.add(get(array[i]))
    }
}
