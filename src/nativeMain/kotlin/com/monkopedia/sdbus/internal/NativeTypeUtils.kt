@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.internal

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

internal interface NativeTypeConverter<K, N : CVariable> {
    fun N.set(k: K)
    fun get(n: N): K
    val size: Long
    val align: Int
    fun allocNative(scope: MemScope, source: List<K>): CArrayPointer<N>
    fun readNativeInto(array: CArrayPointer<N>, size: Int, list: MutableList<K>)
}

internal inline fun <reified K, reified N : CVariable> buildTypeConverter(
    prop: KMutableProperty1<N, K>
): NativeTypeConverter<K, N> = object : NativeTypeConverter<K, N> {
    override fun N.set(k: K) {
        prop.set(this, k)
    }

    override fun get(n: N): K = prop.get(n)

    override val size: Long
        get() = sizeOf<N>()
    override val align: Int
        get() = alignOf<N>()

    override fun readNativeInto(array: CArrayPointer<N>, size: Int, list: MutableList<K>) {
        doReadNativeInto(array, size, list)
    }

    override fun allocNative(scope: MemScope, source: List<K>): CArrayPointer<N> =
        doAllocNative(scope, source)
}

internal val BooleanTypeConverter = buildTypeConverter(BooleanVar::value)
internal val ByteTypeConverter = buildTypeConverter(ByteVar::value)
internal val ShortTypeConverter = buildTypeConverter(ShortVar::value)
internal val IntTypeConverter = buildTypeConverter(IntVar::value)
internal val LongTypeConverter = buildTypeConverter(LongVar::value)
internal val UByteTypeConverter = buildTypeConverter(UByteVar::value)
internal val UShortTypeConverter = buildTypeConverter(UShortVar::value)
internal val UIntTypeConverter = buildTypeConverter(UIntVar::value)
internal val ULongTypeConverter = buildTypeConverter(ULongVar::value)
internal val FloatTypeConverter = buildTypeConverter(FloatVar::value)
internal val DoubleTypeConverter = buildTypeConverter(DoubleVar::value)

internal inline fun <K, reified N : CVariable> NativeTypeConverter<K, N>.doAllocNative(
    scope: MemScope,
    source: List<K>
): CArrayPointer<N> = scope.allocArray(source.size) {
    set(source[it])
}

internal inline fun <K, reified N : CVariable> NativeTypeConverter<K, N>.doReadNativeInto(
    array: CArrayPointer<N>,
    size: Int,
    list: MutableList<K>
) {
    for (i in 0 until size) {
        list.add(get(array[i]))
    }
}
