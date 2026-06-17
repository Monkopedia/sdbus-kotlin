package com.monkopedia.sdbus

actual sealed class TypeSignature actual constructor() {
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
) : TypeSignature()

internal actual val VoidSig: TypeSignature = PrimitiveSig("", valid = true, isTrivial = true)
internal actual val BoolSig: TypeSignature = PrimitiveSig("b", valid = true, isTrivial = true)
internal actual val UByteSig: TypeSignature = PrimitiveSig("y", valid = true, isTrivial = true)
internal actual val ShortSig: TypeSignature = PrimitiveSig("n", valid = true, isTrivial = true)
internal actual val UShortSig: TypeSignature = PrimitiveSig("q", valid = true, isTrivial = true)
internal actual val IntSig: TypeSignature = PrimitiveSig("i", valid = true, isTrivial = true)
internal actual val UIntSig: TypeSignature = PrimitiveSig("u", valid = true, isTrivial = true)
internal actual val LongSig: TypeSignature = PrimitiveSig("x", valid = true, isTrivial = true)
internal actual val ULongSig: TypeSignature = PrimitiveSig("t", valid = true, isTrivial = true)
internal actual val DoubleSig: TypeSignature = PrimitiveSig("d", valid = true, isTrivial = true)
internal actual val StringSig: TypeSignature = PrimitiveSig("s", valid = true, isTrivial = false)
internal actual val ObjectPathSig: TypeSignature =
    PrimitiveSig("o", valid = true, isTrivial = false)
internal actual val SignatureSig: TypeSignature = PrimitiveSig("g", valid = true, isTrivial = false)
internal actual val UnixFdSig: TypeSignature = PrimitiveSig("h", valid = true, isTrivial = false)
internal actual val VariantSig: TypeSignature = PrimitiveSig("v", valid = true, isTrivial = false)
