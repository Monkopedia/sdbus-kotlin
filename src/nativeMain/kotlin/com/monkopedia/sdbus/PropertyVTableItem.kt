package com.monkopedia.sdbus

import com.monkopedia.sdbus.Flags.GeneralFlags.DEPRECATED
import com.monkopedia.sdbus.Flags.GeneralFlags.PRIVILEGED
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0
import kotlinx.cinterop.memScoped

fun registerProperty(propertyName: PropertyName): PropertyVTableItem =
    PropertyVTableItem(propertyName)

fun registerProperty(propertyName: String): PropertyVTableItem =
    registerProperty(PropertyName(propertyName))

inline fun VTableBuilder.prop(propertyName: String, builder: PropertyVTableItem.() -> Unit) {
    prop(PropertyName(propertyName), builder)
}

inline fun VTableBuilder.prop(propertyName: PropertyName, builder: PropertyVTableItem.() -> Unit) {
    items.add(PropertyVTableItem(propertyName).also(builder))
}

data class PropertyVTableItem(
    val name: PropertyName,
    var signature: Signature? = null,
    var getter: PropertyGetCallback? = null,
    var setter: PropertySetCallback? = null,
    val flags: Flags = Flags()
) : VTableItem {
    inline fun <reified T : Any> withGetter(crossinline callback: () -> T): PropertyVTableItem =
        apply {
            if (signature == null) {
                signature = Signature(signatureOf<T>().value)
            }

            getter = { reply ->
                // Get the propety value and serialize it into the pre-constructed reply message
                memScoped {
                    reply.serialize<T>(callback())
                }
            }
        }

    inline fun <reified T : Any> withSetter(crossinline callback: (T) -> Unit): PropertyVTableItem =
        apply {
            if (signature == null) {
                signature = Signature(signatureOf<T>().value)
            }
            setter = { call ->
                // Default-construct property value
                memScoped {
                    // Deserialize property value from the incoming call message
                    val property = call.deserialize<T>()

                    // Invoke setter with the value
                    callback(property)
                }
            }
        }

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

    operator fun plusAssign(behavior: PropertyUpdateBehaviorFlags) {
        flags.set(behavior)
    }

    operator fun PropertyUpdateBehaviorFlags.unaryPlus() {
        flags.set(this)
    }

    inline fun <reified T : Any> with(receiver: KProperty0<T>) {
        signature = Signature(signatureOf<T>().value)
        getter = {
            receiver.get()
        }
        if (receiver is KMutableProperty0) {
            setter = {
                receiver.set(it.deserialize<T>())
            }
        }
    }
}
