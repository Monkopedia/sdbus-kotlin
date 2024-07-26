package org.freedesktop.DBus

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.signal
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class PropertiesAdaptor(
  protected val obj: IObject,
) : Properties {
  public override fun register() {
    obj.addVTable(Properties.Companion.INTERFACE_NAME) {
      method("Get") {
        inputParamNames = listOf("interface_name", "property_name")
        outputParamNames = listOf("value")
        acall(this@PropertiesAdaptor::`get`)
      }
      signal("PropertiesChanged") {
        with<String>("interface_name")
        with<Map<String, Variant>>("changed_properties")
        with<List<String>>("invalidated_properties")
      }
    }
  }

  public suspend fun onPropertiesChanged(
    interfaceName: String,
    changedProperties: Map<String, Variant>,
    invalidatedProperties: List<String>,
  ): Unit = obj.emitSignal("PropertiesChanged") { call(interfaceName, changedProperties,
        invalidatedProperties) }
}
