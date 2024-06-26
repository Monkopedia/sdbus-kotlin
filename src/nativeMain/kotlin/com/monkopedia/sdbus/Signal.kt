@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.ISdBus
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

class Signal private constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus, real: Int) :
    Message(msg, sdbus, real) {

    internal constructor(sdbus: ISdBus) :
        this(null, sdbus, 0)

    internal constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus) : this(msg, sdbus, 0) {
        if (msg != null) {
            sdbus.sd_bus_message_ref(msg)
        }
    }

    internal constructor(
        msg: CPointer<sd_bus_message>,
        sdbus: ISdBus,
        adopt_message: adopt_message_t
    ) : this(msg, sdbus, 0)

    constructor (o: Signal) : this(o.msg, o.sdbus)

    fun setDestination(destination: String) {
        val r = sdbus.sd_bus_message_set_destination(msg, destination)
        sdbusRequire(r < 0, "Failed to set signal destination", -r)
    }

    fun send() {
        val r = sdbus.sd_bus_send(null, msg, null)
        sdbusRequire(r < 0, "Failed to emit signal", -r)
    }
}
