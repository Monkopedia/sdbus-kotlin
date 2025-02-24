/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

fun Signature.maybeDegrouped(groupedReturn: Boolean): Signature {
    if (groupedReturn) {
        require(value[0] == '(' && value.last() == ')') {
            "Value $value is not a struct and can't be degrouped"
        }
        return Signature(value.substring(1, value.length - 1))
    }
    return this
}

/**
 * Since we combine multiple output arguments into a single struct for methods, we need
 * to modify their serialization to not expect a struct to be wrapping them, degrouping does that
 * wrapping a trickery to avoid it.
 */
fun <T> KSerializer<T>.maybeDegrouped(groupedReturn: Boolean): KSerializer<T> =
    if (groupedReturn) this.degrouped() else this

private fun <T> KSerializer<T>.degrouped(): KSerializer<T> {
    return object : KSerializer<T> {
        override val descriptor: SerialDescriptor
            get() = this@degrouped.descriptor

        override fun deserialize(decoder: Decoder): T {
            val degroupedDecoder = decoder.degrouped()
            return this@degrouped.deserialize(degroupedDecoder)
        }

        override fun serialize(encoder: Encoder, value: T) {
            val degroupedEncoder = encoder.degrouped()
            return this@degrouped.serialize(degroupedEncoder, value)
        }
    }
}

private fun unexpectedCall(): Nothing = error("Expected degrouping structured call")

private fun Encoder.degrouped(): Encoder = object : Encoder {
    override val serializersModule: SerializersModule
        get() = this@degrouped.serializersModule

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        // No encoding to do, just return a wrapper that ignores the end.
        return object : CompositeEncoder by (this@degrouped as CompositeEncoder) {
            override fun endStructure(descriptor: SerialDescriptor) {
                // Ignore the end.
            }
        }
    }

    override fun encodeBoolean(value: Boolean) = unexpectedCall()

    override fun encodeByte(value: Byte) = unexpectedCall()

    override fun encodeChar(value: Char) = unexpectedCall()

    override fun encodeDouble(value: Double) = unexpectedCall()

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = unexpectedCall()

    override fun encodeFloat(value: Float) = unexpectedCall()

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = unexpectedCall()

    override fun encodeInt(value: Int) = unexpectedCall()

    override fun encodeLong(value: Long) = unexpectedCall()

    @ExperimentalSerializationApi
    override fun encodeNull() = unexpectedCall()

    override fun encodeShort(value: Short) = unexpectedCall()

    override fun encodeString(value: String) = unexpectedCall()
}

private fun Decoder.degrouped(): Decoder = object : Decoder {
    override val serializersModule: SerializersModule
        get() = this@degrouped.serializersModule

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // No decoding to do, just return a wrapper that ignores the end.
        return object : CompositeDecoder by (this@degrouped as CompositeDecoder) {
            override fun endStructure(descriptor: SerialDescriptor) {
                // Ignore the end.
            }
        }
    }

    override fun decodeBoolean(): Boolean = unexpectedCall()

    override fun decodeByte(): Byte = unexpectedCall()

    override fun decodeChar(): Char = unexpectedCall()

    override fun decodeDouble(): Double = unexpectedCall()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = unexpectedCall()

    override fun decodeFloat(): Float = unexpectedCall()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = unexpectedCall()

    override fun decodeInt(): Int = unexpectedCall()

    override fun decodeLong(): Long = unexpectedCall()

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = unexpectedCall()

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing = unexpectedCall()

    override fun decodeShort(): Short = unexpectedCall()

    override fun decodeString(): String = unexpectedCall()
}
