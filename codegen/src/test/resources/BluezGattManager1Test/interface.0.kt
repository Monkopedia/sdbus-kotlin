package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface GattManager1 {
  public fun register()

  public suspend fun registerApplication(application: ObjectPath, options: Map<String, Variant>)

  public suspend fun unregisterApplication(application: ObjectPath)

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.GattManager1"
  }
}
