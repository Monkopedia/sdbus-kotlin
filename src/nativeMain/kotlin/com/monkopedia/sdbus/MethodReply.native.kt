@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.ISdBus
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

actual class MethodReply internal constructor(
    msg: CPointer<sd_bus_message>?,
    sdbus: ISdBus,
    adoptMessage: Boolean = false
) : Message(msg, sdbus, adoptMessage) {

    internal constructor(sdbus: ISdBus) : this(null, sdbus)

    constructor (o: MethodReply) : this(o.msg, o.sdbus)

    actual fun send() {
        val r = sdbus.sd_bus_send(null, msg, null)
        sdbusRequire(r < 0, "Failed to send reply", -r)
    }
}
