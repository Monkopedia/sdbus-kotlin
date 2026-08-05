package org.freedesktop.two.dbus

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.signalFlow
import kotlin.String
import kotlinx.coroutines.flow.Flow

/**
 * Standard property getter/setter interface
 *
 * Interface for all objects which expose properties on the bus, allowing those properties to be
 * got, set, and signals emitted to notify of changes to the property values.
 */
public class PropertiesProxy(
  public val proxy: Proxy,
) : Properties {
  /**
   * Emitted when one or more properties change values on @interface_name. A property may be listed
   * in @changed_properties or @invalidated_properties depending on whether the service wants to
   * broadcast the property’s new value. If a value is large or infrequently used, the service might
   * not want to broadcast it, and will wait for clients to request it instead.
   */
  public val propertiesChanged: Flow<PropertiesChanged> =
      proxy.signalFlow(Properties.Companion.INTERFACE_NAME, SignalName("PropertiesChanged")) {
        call(::PropertiesChanged)
      }

  public override fun register() {
  }

  /**
   * Retrieves the value of the property at @property_name on @interface_name on this object.
   * If @interface_name is an empty string, all interfaces will be searched for @property_name; if
   * multiple properties match, the result is undefined. If @interface_name or @property_name do not
   * exist, a #org.freedesktop.DBus.Error.InvalidArgs error is returned.
   *
   * @param interfaceName Name of the interface the property is defined on.
   * @param propertyName Name of the property to get.
   * @return Property value, wrapped in a variant.
   */
  override suspend fun `get`(interfaceName: String, propertyName: String): Variant = proxy.callMethodAsync(Properties.Companion.INTERFACE_NAME, MethodName("Get")) {
    call(interfaceName, propertyName)
  }
}
