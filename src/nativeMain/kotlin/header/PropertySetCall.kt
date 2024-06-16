@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import cnames.structs.sd_bus_message
import com.monkopedia.sdbus.internal.ISdBus
import kotlin.native.internal.NativePtr
import kotlin.native.internal.NativePtr.Companion
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.interpretCPointer

class PropertySetCall private constructor(
    msg_: CPointer<sd_bus_message>?,
    sdbus_: ISdBus,
    real: Int
) : Message(msg_, sdbus_, real) {

    constructor(sdbus: ISdBus) :
        this(null, sdbus, 0)

    constructor(msg: CPointer<sd_bus_message>?, sdbus: ISdBus) : this(msg, sdbus, 0) {
        sdbus.sd_bus_message_ref(msg)
    }

    constructor (o: PropertySetCall) : this(o.msg_, o.sdbus_)
    constructor(msg: CPointer<sd_bus_message>, sdbus: ISdBus, adopt_message: adopt_message_t) :
        this(msg, sdbus, 0)
}
