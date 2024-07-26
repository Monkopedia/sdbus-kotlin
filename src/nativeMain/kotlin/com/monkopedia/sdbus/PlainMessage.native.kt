@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.IConnection.Companion.getPseudoConnectionInstance
import com.monkopedia.sdbus.internal.ISdBus
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

// Represents any of the above message types, or just a message that serves as a container for data
actual class PlainMessage internal constructor(
    msg: CPointer<sd_bus_message>?,
    sdbus: ISdBus,
    adoptMessage: Boolean = false
) : Message(msg, sdbus, adoptMessage) {

    constructor(o: PlainMessage) : this(o.msg, o.sdbus)

    actual companion object {
        actual fun createPlainMessage(): PlainMessage =
            getPseudoConnectionInstance().createPlainMessage()
    }
}
