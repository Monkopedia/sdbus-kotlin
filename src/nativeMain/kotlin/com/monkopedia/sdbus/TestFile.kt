@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus.testing

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.signal
import com.monkopedia.sdbus.testing.Properties.Companion.INTERFACE_NAME
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

public interface Properties {
    public fun register()

    public suspend fun `get`(interfaceName: String, propertyName: String): Variant

    public suspend fun onPropertiesChanged(
        interfaceName: String,
        changedProperties: Map<String, Variant>,
        invalidatedProperties: List<String>
    )

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

public abstract class PropertiesAdapter(protected val obj: IObject) : Properties {

    override fun register() {
        obj.addVTable(INTERFACE_NAME) {
            method("Get") {
                inputParamNames = listOf("interface_name", "property_name")
                outputParamNames = listOf("value")
                acall(this@PropertiesAdapter::`get`)
            }
            signal("PropertiesChanged") {
                with<String>("interface_name")
                with<Map<String, Variant>>("changed_properties")
                with<List<String>>("invalidated_properties")
            }
        }
    }

    override suspend fun onPropertiesChanged(
        interfaceName: String,
        changedProperties: Map<String, Variant>,
        invalidatedProperties: List<String>
    ) {
        obj.emitSignal("PropertiesChanged") {
            call(interfaceName, changedProperties, invalidatedProperties)
        }
    }
}

public abstract class PropertiesProxy(protected val proxy: IProxy) : Properties {

    override fun register() {
        val weakRef = WeakReference(this)
        proxy.onSignal(INTERFACE_NAME, "PropertiesChanged") {
            acall {
                    interfaceName: String,
                    changedProperties: Map<String, Variant>,
                    invalidatedProperties: List<String>
                ->
                weakRef.get()
                    ?.onPropertiesChanged(interfaceName, changedProperties, invalidatedProperties)
                    ?: Unit
            }
        }
    }

    override suspend fun get(interfaceName: String, propertyName: String): Variant {
        return proxy.callMethodAsync(INTERFACE_NAME, "Get") {
            call(interfaceName, propertyName)
        }
    }
}
