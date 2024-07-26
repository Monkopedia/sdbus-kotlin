package org.bluez

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface GattManager1 {
  public fun register()

  public suspend fun registerApplication(application: ObjectPath, options: Map<String, Variant>)

  public suspend fun unregisterApplication(application: ObjectPath)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.GattManager1")
  }
}
