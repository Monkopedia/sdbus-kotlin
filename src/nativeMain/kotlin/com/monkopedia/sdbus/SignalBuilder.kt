package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED

fun registerSignal(signalName: SignalName): SignalVTableItem = SignalVTableItem(signalName)

fun registerSignal(signalName: String): SignalVTableItem = registerSignal(SignalName(signalName))

inline fun VTableBuilder.signal(signal: String, builder: SignalVTableItem.() -> Unit) {
    signal(SignalName(signal), builder)
}

inline fun VTableBuilder.signal(signal: SignalName, builder: SignalVTableItem.() -> Unit) {
    items.add(SignalVTableItem(signal).also(builder))
}

data class SignalVTableItem(
    val name: SignalName,
    var signature: Signature = Signature(""),
    var paramNames: List<String> = emptyList(),
    val flags: Flags = Flags()
) : VTableItem {

    inline fun <reified T> with(paramName: String) {
        signature += signatureOf<T>().value
        paramNames = paramNames + paramName
    }

    var isDeprecated: Boolean
        get() = flags.test(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }
}
