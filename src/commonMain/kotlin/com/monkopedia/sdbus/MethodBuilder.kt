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

/**
 * A vtable entry describing a method exported by an [Object].
 *
 * Construct one inside an [addVTable] block via [method]. The input/output signatures are usually
 * derived automatically when binding a handler via [call]/[asyncCall]/[implementedAs]; the parameter
 * name lists may be set for introspection.
 *
 * @property name The method name
 * @property inputSignature D-Bus signature of the input arguments, or `null` until bound
 * @property inputParamNames Names of the input parameters, used for introspection
 * @property outputSignature D-Bus signature of the return value, or `null` until bound
 * @property outputParamNames Names of the output parameters, used for introspection
 * @property callbackHandler The handler invoked when the method is called, or `null` until bound
 * @property flags Behavioral flags for this method
 */
class MethodVTableItem internal constructor(
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

    override fun createAsyncCall(
        method: TypedMethod,
        handler: suspend (List<Any?>) -> Any?,
        errorCall: (suspend (Throwable?) -> Unit)?,
        coroutineContext: CoroutineContext
    ): TypedMethodCall<*> = super.createAsyncCall(method, handler, errorCall, coroutineContext)
        .also(this::implementedAs)

    /**
     * Binds this method to the given typed callback, deriving the input/output signatures from it.
     *
     * @param callback The typed method call produced by [call] or [asyncCall]
     * @return This item, for chaining
     */
    fun implementedAs(callback: TypedMethodCall<*>): MethodVTableItem = apply {
        val inputType = callback.method.inputType
        inputSignature = Signature(inputType.joinToString("") { it.signature.value })
        outputSignature = Signature(callback.method.outputType.signature.value)
            .maybeDegrouped(outputParamNames.size > 1)

        callbackHandler = { call ->
            callback.invoke(
                call,
                onSuccess = { type, result ->
                    call.createReply().also {
                        it.serialize(type, result, outputParamNames.size > 1)
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

    /** Whether this method is marked deprecated. */
    var isDeprecated: Boolean
        get() = flags.has(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }

    /** Whether this method is marked as requiring privileged access. */
    var isPrivileged: Boolean
        get() = flags.has(PRIVILEGED)
        set(value) {
            flags.set(PRIVILEGED, value)
        }

    /** Whether this method does not produce a reply. */
    var hasNoReply: Boolean
        get() = flags.has(METHOD_NO_REPLY)
        set(value) {
            flags.set(METHOD_NO_REPLY, value)
        }
}
