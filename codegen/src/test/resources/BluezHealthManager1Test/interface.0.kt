package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface HealthManager1 {
  public fun register()

  public suspend fun createApplication(config: Map<String, Variant>): ObjectPath

  public suspend fun destroyApplication(application: ObjectPath)

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.HealthManager1"
  }
}
