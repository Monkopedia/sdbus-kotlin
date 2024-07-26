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

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import kotlin.coroutines.CoroutineContext

/**
 * Builds a method to be added to the vtable in progress.
 *
 * @see addVTable
 */
fun VTableBuilder.method(methodName: MethodName, builder: MethodVTableItem.() -> Unit) {
    items.add(MethodVTableItem(methodName).also(builder))
}

data class MethodVTableItem(
    val name: MethodName,
    var inputSignature: Signature? = null,
    var inputParamNames: List<String> = emptyList(),
    var outputSignature: Signature? = null,
    var outputParamNames: List<String> = emptyList(),
    var callbackHandler: MethodCallback? = null,
    val flags: Flags = Flags()
) : TypedMethodBuilderContext(),
    VTableItem {
    override fun createCall(
        method: TypedMethod,
        handler: (List<Any?>) -> Any?,
        errorCall: ((Throwable?) -> Unit)?
    ): TypedMethodCall<*> = super.createCall(method, handler, errorCall).also(this::implementedAs)

    override fun createACall(
        method: TypedMethod,
        handler: suspend (List<Any?>) -> Any?,
        errorCall: (suspend (Throwable?) -> Unit)?,
        coroutineContext: CoroutineContext
    ): TypedMethodCall<*> = super.createACall(method, handler, errorCall, coroutineContext)
        .also(this::implementedAs)

    fun implementedAs(callback: TypedMethodCall<*>): MethodVTableItem = apply {
        val inputType = callback.method.inputType
        inputSignature = Signature(inputType.joinToString("") { it.signature.value })
        outputSignature = Signature(callback.method.outputType.signature.value)
        callbackHandler = { call ->
            callback.invoke(
                call,
                onSuccess = { type, result ->
                    call.createReply().also {
                        it.serialize(type, result)
                    }
                },
                onFailure = {
                    call.createErrorReply(it.toError())
                },
                onResult = {
                    it.send()
                }
            )
        }
    }

    var isDeprecated: Boolean
        get() = flags.test(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }
    var isPrivileged: Boolean
        get() = flags.test(PRIVILEGED)
        set(value) {
            flags.set(PRIVILEGED, value)
        }
    var hasNoReply: Boolean
        get() = flags.test(METHOD_NO_REPLY)
        set(value) {
            flags.set(METHOD_NO_REPLY, value)
        }
}
