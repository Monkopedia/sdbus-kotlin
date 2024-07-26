package org.freedesktop.DBus

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Variant
import kotlin.String

public interface Properties {
  public fun register()

  public suspend fun `get`(interfaceName: String, propertyName: String): Variant

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.freedesktop.DBus.Properties")
  }
}
