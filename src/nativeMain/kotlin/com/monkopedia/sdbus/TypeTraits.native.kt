@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.BooleanTypeConverter
import com.monkopedia.sdbus.internal.DoubleTypeConverter
import com.monkopedia.sdbus.internal.IntTypeConverter
import com.monkopedia.sdbus.internal.LongTypeConverter
import com.monkopedia.sdbus.internal.NativeTypeConverter
import com.monkopedia.sdbus.internal.ShortTypeConverter
import com.monkopedia.sdbus.internal.UByteTypeConverter
import com.monkopedia.sdbus.internal.UIntTypeConverter
import com.monkopedia.sdbus.internal.ULongTypeConverter
import com.monkopedia.sdbus.internal.UShortTypeConverter
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar

actual sealed class SdbusSig actual constructor() {
    @PublishedApi
    internal actual abstract val value: String

    @PublishedApi
    internal actual abstract val valid: Boolean

    @PublishedApi
    internal actual abstract val isTrivial: Boolean
}

data class PrimitiveSig<K, N : CVariable> internal constructor(
    override val value: String,
    override val valid: Boolean = false,
    override val isTrivial: Boolean = false,
    internal val converter: NativeTypeConverter<K, N>? = null
) : SdbusSig()

actual val VoidSig: SdbusSig =
    PrimitiveSig<Unit, COpaquePointerVar>("", valid = true, isTrivial = false)

actual val BoolSig: SdbusSig =
    PrimitiveSig("b", valid = true, isTrivial = true, BooleanTypeConverter)

actual val UByteSig: SdbusSig =
    PrimitiveSig("y", valid = true, isTrivial = true, UByteTypeConverter)

actual val ShortSig: SdbusSig =
    PrimitiveSig("n", valid = true, isTrivial = true, ShortTypeConverter)

actual val UShortSig: SdbusSig =
    PrimitiveSig("q", valid = true, isTrivial = true, UShortTypeConverter)

actual val IntSig: SdbusSig = PrimitiveSig("i", valid = true, isTrivial = true, IntTypeConverter)

actual val UIntSig: SdbusSig = PrimitiveSig("u", valid = true, isTrivial = true, UIntTypeConverter)

actual val LongSig: SdbusSig = PrimitiveSig("x", valid = true, isTrivial = true, LongTypeConverter)

actual val ULongSig: SdbusSig =
    PrimitiveSig("t", valid = true, isTrivial = true, ULongTypeConverter)

actual val DoubleSig: SdbusSig =
    PrimitiveSig("d", valid = true, isTrivial = true, DoubleTypeConverter)

actual val StringSig: SdbusSig =
    PrimitiveSig<String, CPointerVar<ByteVar>>("s", valid = true, isTrivial = false)

actual val ObjectPathSig: SdbusSig =
    PrimitiveSig<ObjectPath, CPointerVar<ByteVar>>("o", valid = true, isTrivial = false)

actual val SignatureSig: SdbusSig =
    PrimitiveSig<Signature, CPointerVar<ByteVar>>("g", valid = true, isTrivial = false)

actual val UnixFdSig: SdbusSig = PrimitiveSig<UnixFd, IntVar>("h", valid = true, isTrivial = false)

actual val VariantSig: SdbusSig =
    PrimitiveSig<Any, COpaquePointerVar>("v", valid = true, isTrivial = false)
