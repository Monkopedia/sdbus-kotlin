package com.monkopedia.sdbus

/**
 * Emits signal on D-Bus
 *
 * @param signalName Name of the signal
 * @return A helper object for convenient emission of signals
 *
 * This is a high-level, convenience way of emitting D-Bus signals that abstracts
 * from the D-Bus message concept. Signal arguments are automatically serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments.
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun Object.emitSignal(
    interfaceName: InterfaceName = InterfaceName(""),
    signalName: SignalName = SignalName(""),
    builder: SignalEmitter.() -> Unit
) = emit(SignalEmitter(interfaceName, signalName).also(builder))

fun Object.emit(signal: SignalEmitter) {
    val m = createSignal(signal.interfaceName, signal.signalName)
    m.serialize(
        signal.typedMethodArguments ?: buildArgs {
            call()
        }
    )
    emitSignal(m)
}

data class SignalEmitter(
    var interfaceName: InterfaceName = InterfaceName(""),
    var signalName: SignalName = SignalName(""),
    var typedMethodArguments: TypedArguments? = null
) : TypedArgumentsBuilderContext() {

    override fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        super.createCall(inputType, values).also {
            typedMethodArguments = it
        }
}
