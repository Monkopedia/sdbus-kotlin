@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import com.monkopedia.sdbus.header.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.header.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.header.Flags.GeneralFlags.PRIVILEGED
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

sealed class VTableItem

data class MethodVTableItem(
    val name: MethodName,
    var inputSignature: Signature? = null,
    var inputParamNames: List<String> = emptyList(),
    var outputSignature: Signature? = null,
    var outputParamNames: List<String> = emptyList(),
    var callbackHandler: method_callback? = null,
    val flags: Flags = Flags(),
) : VTableItem() {
    inline fun implementedAs(callback: TypedMethodBuilder): MethodVTableItem =
        implementedAs(build(callback))

    fun implementedAs(callback: TypedMethodCall): MethodVTableItem = apply {
        val inputType = callback.method.inputType
        inputSignature = Signature(inputType.joinToString("") { it.signature.value })
        outputSignature = Signature(callback.method.outputType.signature.value)
        callbackHandler = { call ->
            val asyncCall = callback.asAsyncMethod()
            runCatching {
                val args = call.deserialize(callback.method)
                asyncCall.handler.invoke(args)
            }.fold(
                onSuccess = { result ->
                    call.createReply().also {
                        @Suppress("UNCHECKED_CAST")
                        it.serialize(asyncCall.method.outputType as Typed<Any>, result!!)
                    }
                },
                onFailure = {
                    call.createErrorReply(it.toError())
                }
            ).send()
        }
    }


    fun withInputParamNames(names: List<String>): MethodVTableItem = apply {
        inputParamNames = names
    }

    fun withInputParamNames(vararg names: String): MethodVTableItem = apply {
        inputParamNames = names.toList()
    }

    fun withOutputParamNames(names: List<String>): MethodVTableItem = apply {
        outputParamNames = names
    }

    fun withOutputParamNames(vararg names: String): MethodVTableItem = apply {
        outputParamNames = names.toList()
    }

    fun markAsDeprecated(): MethodVTableItem = apply {
        flags.set(DEPRECATED)
    }

    fun markAsPrivileged(): MethodVTableItem = apply {
        flags.set(PRIVILEGED)
    }

    fun withNoReply(): MethodVTableItem = apply {
        flags.set(METHOD_NO_REPLY)
    }
}

internal fun <T : Any> Message.serialize(outputType: Typed<T>, result: T) {
    val module = serializersModuleOf(outputType.cls, outputType.type)
    serialize(outputType.type, module, result)
}

fun registerMethod(methodName: MethodName): MethodVTableItem {
    return MethodVTableItem(methodName)
}

fun registerMethod(methodName: String): MethodVTableItem = registerMethod(MethodName(methodName))

data class SignalVTableItem(
    val name: SignalName,
    var signature: Signature = Signature(""),
    var paramNames: List<String> = emptyList(),
    val flags: Flags = Flags()
) : VTableItem() {
    inline fun <reified T> withParameters(): SignalVTableItem = apply {
        signature = Signature(signature_of<T>().value)
    }

    fun withParameters(names: List<String>): SignalVTableItem = apply {
        paramNames = names
    }

    inline fun <reified T> withParameters(vararg names: String): SignalVTableItem = apply {
        signature = Signature(signature_of<Array<T>>().value)
        paramNames = names.toList()
    }

    fun withParameters(vararg names: String): SignalVTableItem = apply {
        paramNames = names.toList()
    }

    fun markAsDeprecated(): SignalVTableItem = apply {
        flags.set(DEPRECATED)
    }

};

fun registerSignal(signalName: SignalName): SignalVTableItem {
    return SignalVTableItem(signalName)
}

fun registerSignal(signalName: String): SignalVTableItem = registerSignal(SignalName(signalName))

data class PropertyVTableItem(
    val name: PropertyName,
    var signature: Signature? = null,
    var getter: property_get_callback? = null,
    var setter: property_set_callback? = null,
    val flags: Flags = Flags()
) : VTableItem() {
    inline fun <reified T : Any> withGetter(crossinline callback: () -> T): PropertyVTableItem =
        apply {
            if (signature == null) {
                signature = Signature(signature_of<T>().value)
            }

            getter = { reply ->
                // Get the propety value and serialize it into the pre-constructed reply message
                memScoped {
                    reply.serialize<T>(callback())
                }
            };
        }

    inline fun <reified T : Any> withSetter(crossinline callback: (T) -> Unit): PropertyVTableItem =
        apply {
            if (signature == null) {
                signature = Signature(signature_of<T>().value)
            }
            setter = { call ->
                // Default-construct property value
                memScoped {
                    // Deserialize property value from the incoming call message
                    val property = call.deserialize<T>()

                    // Invoke setter with the value
                    callback(property);
                }
            };
        }

    fun markAsDeprecated(): PropertyVTableItem = apply {
        flags.set(DEPRECATED)
    }

    fun markAsPrivileged(): PropertyVTableItem = apply {
        flags.set(PRIVILEGED)
    }

    fun withUpdateBehavior(behavior: Flags.PropertyUpdateBehaviorFlags): PropertyVTableItem =
        apply {
            flags.set(behavior)
        }

};

fun registerProperty(propertyName: PropertyName): PropertyVTableItem =
    PropertyVTableItem(propertyName)

fun registerProperty(propertyName: String): PropertyVTableItem =
    registerProperty(PropertyName(propertyName))

data class InterfaceFlagsVTableItem(
    val flags: Flags = Flags()
) : VTableItem() {
    fun markAsDeprecated(): InterfaceFlagsVTableItem = apply {
        flags.set(DEPRECATED)
    }

    fun markAsPrivileged(): InterfaceFlagsVTableItem = apply {
        flags.set(PRIVILEGED)
    }

    fun withNoReplyMethods(): InterfaceFlagsVTableItem = apply {
        flags.set(METHOD_NO_REPLY)
    }

    fun withPropertyUpdateBehavior(
        behavior: Flags.PropertyUpdateBehaviorFlags
    ): InterfaceFlagsVTableItem = apply {
        flags.set(behavior)
    }
};

fun setInterfaceFlags(): InterfaceFlagsVTableItem = InterfaceFlagsVTableItem()

