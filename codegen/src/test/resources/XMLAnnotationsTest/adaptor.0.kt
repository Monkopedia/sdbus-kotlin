package org.freedesktop.two.dbus

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.signal
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public abstract class PropertiesAdaptor(
  public val obj: Object,
) : Properties {
  public override fun register() {
    obj.addVTable(Properties.Companion.INTERFACE_NAME) {
      method(MethodName("Get")) {
        inputParamNames = listOf("interface_name", "property_name")
        outputParamNames = listOf("value")
        acall(this@PropertiesAdaptor::`get`)
      }
      signal(SignalName("PropertiesChanged")) {
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
  ): Unit = obj.emitSignal(Properties.Companion.INTERFACE_NAME, SignalName("PropertiesChanged")) {
    call(interfaceName, changedProperties, invalidatedProperties)
  }
}
