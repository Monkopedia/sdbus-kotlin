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

/**
 * Standard property getter/setter interface
 *
 * Interface for all objects which expose properties on the bus, allowing those properties to be
 * got, set, and signals emitted to notify of changes to the property values.
 */
public abstract class PropertiesAdaptor(
  public val obj: Object,
) : Properties {
  public override fun register() {
    obj.addVTable(Properties.Companion.INTERFACE_NAME) {
      method(MethodName("Get")) {
        inputParamNames = listOf("interface_name", "property_name")
        outputParamNames = listOf("value")
        asyncCall(this@PropertiesAdaptor::`get`)
      }
      signal(SignalName("PropertiesChanged")) {
        with<String>("interface_name")
        with<Map<String, Variant>>("changed_properties")
        with<List<String>>("invalidated_properties")
      }
    }
  }

  /**
   * Emitted when one or more properties change values on @interface_name. A property may be listed
   * in @changed_properties or @invalidated_properties depending on whether the service wants to
   * broadcast the property’s new value. If a value is large or infrequently used, the service might
   * not want to broadcast it, and will wait for clients to request it instead.
   *
   * @param interfaceName Name of the interface the properties changed on.
   * @param changedProperties Map of property name to updated value for the changed properties.
   * @param invalidatedProperties List of names of other properties which have changed, but whose
   * updated values are not notified.
   */
  public suspend fun onPropertiesChanged(
    interfaceName: String,
    changedProperties: Map<String, Variant>,
    invalidatedProperties: List<String>,
  ): Unit = obj.emitSignal(Properties.Companion.INTERFACE_NAME, SignalName("PropertiesChanged")) {
    call(interfaceName, changedProperties, invalidatedProperties)
  }
}
