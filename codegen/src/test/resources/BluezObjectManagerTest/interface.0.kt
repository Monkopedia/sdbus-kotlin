package org.freedesktop.DBus

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface ObjectManager {
  public fun register()

  public suspend fun getManagedObjects(): Map<ObjectPath, Map<String, Map<String, Variant>>>

  public companion object {
    public const val INTERFACE_NAME: String = "org.freedesktop.DBus.ObjectManager"
  }
}
