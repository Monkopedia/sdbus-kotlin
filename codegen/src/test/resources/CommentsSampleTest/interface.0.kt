package org.freedesktop.DBus

import com.monkopedia.sdbus.Variant
import kotlin.String

public interface Properties {
  public fun register()

  public suspend fun `get`(interfaceName: String, propertyName: String): Variant

  public companion object {
    public const val INTERFACE_NAME: String = "org.freedesktop.DBus.Properties"
  }
}
