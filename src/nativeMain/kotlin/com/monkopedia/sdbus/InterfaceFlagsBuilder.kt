package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.METHOD_NO_REPLY
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags

fun setInterfaceFlags(): InterfaceFlagsVTableItem = InterfaceFlagsVTableItem()

inline fun VTableBuilder.interfaceFlags(builder: InterfaceFlagsVTableItem.() -> Unit) {
    items.add(InterfaceFlagsVTableItem().also(builder))
}

data class InterfaceFlagsVTableItem(val flags: Flags = Flags()) : VTableItem {

    var isDeprecated: Boolean
        get() = flags.test(DEPRECATED)
        set(value) {
            flags.set(DEPRECATED, value)
        }
    var isPrivileged: Boolean
        get() = flags.test(PRIVILEGED)
        set(value) {
            flags.set(PRIVILEGED, value)
        }
    var hasNoReply: Boolean
        get() = flags.test(METHOD_NO_REPLY)
        set(value) {
            flags.set(METHOD_NO_REPLY, value)
        }

    operator fun plusAssign(behavior: PropertyUpdateBehaviorFlags) {
        flags.set(behavior)
    }

    operator fun PropertyUpdateBehaviorFlags.unaryPlus() {
        flags.set(this)
    }
}
