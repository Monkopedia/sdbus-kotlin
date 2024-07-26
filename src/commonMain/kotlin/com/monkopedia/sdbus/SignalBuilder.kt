package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED

/**
 * Builds a signal into the given vtable builder.
 *
 * @see addVTable
 */
inline fun VTableBuilder.signal(signal: SignalName, builder: SignalVTableItem.() -> Unit) {
    items.add(SignalVTableItem(signal).also(builder))
}

data class SignalVTableItem(
    val name: SignalName,
    var signature: Signature = Signature(""),
    var paramNames: List<String> = emptyList(),
    val flags: Flags = Flags()
) : VTableItem {

    /**
     * Adds a single parameter to the signal type.
     *
     * This is expected to be called once for each parameter of the signal.
     */
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
