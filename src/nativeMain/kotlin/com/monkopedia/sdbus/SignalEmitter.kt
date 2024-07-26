package com.monkopedia.sdbus

/*!
 * @brief Emits signal on D-Bus
 *
 * @param[in] signalName Name of the signal
 * @return A helper object for convenient emission of signals
 *
 * This is a high-level, convenience way of emitting D-Bus signals that abstracts
 * from the D-Bus message concept. Signal arguments are automatically serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments.
 *
 * Example of use:
 * @code
 * int arg1 = ...;
 * double arg2 = ...;
 * SignalName fooSignal{"fooSignal"};
 * object_.emitSignal(fooSignal).onInterface("com.kistler.foo").withArguments(arg1, arg2);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun IObject.emitSignal(
    interfaceName: String = "",
    signalName: String = "",
    builder: SignalEmitter.() -> Unit
) = emit(SignalEmitter(interfaceName, signalName).also(builder))

fun IObject.emit(signal: SignalEmitter) {
    val m = createSignal(signal.intf, signal.signal)
    m.serialize(
        signal.typedMethodArguments ?: buildArgs {
            call()
        }
    )
    emitSignal(m)
}

data class SignalEmitter(
    var intf: String = "",
    var signal: String = "",
    var typedMethodArguments: TypedArguments? = null
) : TypedArgumentsBuilderContext() {
    var interfaceName: InterfaceName
        get() = InterfaceName(intf)
        set(value) {
            intf = value.value
        }
    var signalName: SignalName
        get() = SignalName(signal)
        set(value) {
            signal = value.value
        }

    override fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        super.createCall(inputType, values).also {
            typedMethodArguments = it
        }
}
