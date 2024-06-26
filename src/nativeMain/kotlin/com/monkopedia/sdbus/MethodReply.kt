@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.ISdBus
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

class MethodReply private constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus, real: Int) :
    Message(msg, sdbus, real) {

    internal constructor(sdbus: ISdBus) : this(null, sdbus, 0)

    internal constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus) : this(msg, sdbus, 0) {
        sdbus.sd_bus_message_ref(msg)
    }

    internal constructor(
        msg: CPointer<sd_bus_message>,
        sdbus: ISdBus,
        adopt_message: adopt_message_t
    ) : this(msg, sdbus, 0)

    constructor (o: MethodReply) : this(o.msg, o.sdbus)

    fun send() {
        val r = sdbus.sd_bus_send(null, msg, null)
        sdbusRequire(r < 0, "Failed to send reply", -r)
    }
}
