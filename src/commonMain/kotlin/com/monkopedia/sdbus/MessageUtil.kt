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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

@PublishedApi
internal inline fun <reified T : Any> Message.serialize(arg: T) {
    serialize(serializer<T>(), serializersModuleOf(serializer<T>()), arg)
}

@PublishedApi
internal expect fun <T> Message.serialize(
    serializer: SerializationStrategy<T>,
    module: SerializersModule,
    arg: T
)

@PublishedApi
internal inline fun <reified T : Any> Message.deserialize(): T =
    deserialize(serializer<T>(), serializersModuleOf(serializer<T>()))

@PublishedApi
internal expect fun <T : Any> Message.deserialize(
    serializer: DeserializationStrategy<T>,
    module: SerializersModule
): T

internal fun Message.append(variant: Variant) {
    variant.serializeTo(this)
}

internal expect inline fun <T> Message.deserializeArrayFast(
    signature: SdbusSig,
    items: MutableList<T>
)

internal fun TypedArguments.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST")
    val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.serialize(
    types: List<KSerializer<*>>,
    args: List<Any>,
    module: SerializersModule
) {
    for ((s, a) in types.zip(args)) {
        @Suppress("UNCHECKED_CAST")
        serialize(s as KSerializer<Any>, module, a)
    }
}

@PublishedApi
internal fun Message.serialize(typedArgs: TypedArguments) {
    val types = typedArgs.inputType.map { it.type }
    val args = typedArgs.values
    val module = typedArgs.module()
    serialize(types, args, module)
}

internal fun TypedMethod.module(): SerializersModule {
    @Suppress("UNCHECKED_CAST")
    val serializer =
        (inputType.firstOrNull() ?: typed<Variant>()) as Typed<Any>
    return serializersModuleOf(serializer.cls, serializer.type)
}

internal fun Message.deserialize(
    types: List<KSerializer<*>>,
    module: SerializersModule
): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return types.map { deserialize(it as KSerializer<Any>, module) }
}

internal fun Message.deserialize(typedArgs: TypedMethod): List<Any> {
    val types = typedArgs.inputType.map { it.type }
    val module = typedArgs.module()
    return deserialize(types, module)
}

internal fun <T : Any> Message.serialize(outputType: Typed<T>, result: T) {
    val module = serializersModuleOf(outputType.cls, outputType.type)
    serialize(outputType.type, module, result)
}
