package org.freedesktop.two.dbus

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Variant
import kotlin.String

/**
 * Standard property getter/setter interface
 *
 * Interface for all objects which expose properties on the bus, allowing those properties to be
 * got, set, and signals emitted to notify of changes to the property values.
 */
public interface Properties {
  public fun register()

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
  public suspend fun `get`(interfaceName: String, propertyName: String): Variant

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.freedesktop.two.DBus.Properties")
  }
}
