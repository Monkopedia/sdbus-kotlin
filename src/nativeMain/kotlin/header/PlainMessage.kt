@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.IConnection.Companion.getPseudoConnectionInstance
import com.monkopedia.sdbus.internal.ISdBus
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

// Represents any of the above message types, or just a message that serves as a container for data
class PlainMessage private constructor(
    msg_: CPointer<sd_bus_message>?,
    sdbus_: ISdBus,
    real: Int
) : Message(msg_, sdbus_, real) {

    constructor(sdbus: ISdBus) : this(null, sdbus, 0)

    constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus) : this(msg, sdbus, 0) {
        if (msg != null) {
            sdbus.sd_bus_message_ref(msg)
        }
    }

    constructor(o: PlainMessage) : this(o.msg_, o.sdbus_)

    constructor(msg: CPointer<sd_bus_message>, sdbus: ISdBus, adopt_message: adopt_message_t) :
        this(msg, sdbus, 0)

    companion object {
        fun createPlainMessage(): PlainMessage {
            return getPseudoConnectionInstance().createPlainMessage()
        }
    }
}
