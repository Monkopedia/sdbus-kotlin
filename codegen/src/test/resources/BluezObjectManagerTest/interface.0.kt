package org.freedesktop.DBus

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface ObjectManager {
  public fun register()

  public suspend fun getManagedObjects(): Map<ObjectPath, Map<String, Map<String, Variant>>>

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.freedesktop.DBus.ObjectManager")
  }
}
