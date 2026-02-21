package com.monkopedia.sdbus

actual sealed class SdbusSig actual constructor() {
    @PublishedApi
    internal actual abstract val value: String

    @PublishedApi
    internal actual abstract val valid: Boolean

    @PublishedApi
    internal actual abstract val isTrivial: Boolean
}

private data class PrimitiveSig(
    override val value: String,
    override val valid: Boolean,
    override val isTrivial: Boolean
) : SdbusSig()

actual val VoidSig: SdbusSig = PrimitiveSig("", valid = true, isTrivial = true)
actual val BoolSig: SdbusSig = PrimitiveSig("b", valid = true, isTrivial = true)
actual val UByteSig: SdbusSig = PrimitiveSig("y", valid = true, isTrivial = true)
actual val ShortSig: SdbusSig = PrimitiveSig("n", valid = true, isTrivial = true)
actual val UShortSig: SdbusSig = PrimitiveSig("q", valid = true, isTrivial = true)
actual val IntSig: SdbusSig = PrimitiveSig("i", valid = true, isTrivial = true)
actual val UIntSig: SdbusSig = PrimitiveSig("u", valid = true, isTrivial = true)
actual val LongSig: SdbusSig = PrimitiveSig("x", valid = true, isTrivial = true)
actual val ULongSig: SdbusSig = PrimitiveSig("t", valid = true, isTrivial = true)
actual val DoubleSig: SdbusSig = PrimitiveSig("d", valid = true, isTrivial = true)
actual val StringSig: SdbusSig = PrimitiveSig("s", valid = true, isTrivial = false)
actual val ObjectPathSig: SdbusSig = PrimitiveSig("o", valid = true, isTrivial = false)
actual val SignatureSig: SdbusSig = PrimitiveSig("g", valid = true, isTrivial = false)
actual val UnixFdSig: SdbusSig = PrimitiveSig("h", valid = true, isTrivial = false)
actual val VariantSig: SdbusSig = PrimitiveSig("v", valid = true, isTrivial = false)
