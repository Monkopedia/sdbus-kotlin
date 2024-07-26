package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import kotlin.coroutines.CoroutineContext

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
