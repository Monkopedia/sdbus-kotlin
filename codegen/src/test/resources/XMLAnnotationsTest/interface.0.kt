package org.freedesktop.two.DBus

import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public interface Properties {
  public fun register()

  public suspend fun `get`(interfaceName: String, propertyName: String): Variant

  public suspend fun onPropertiesChanged(
    interfaceName: String,
    changedProperties: Map<String, Variant>,
    invalidatedProperties: List<String>,
  )

  public companion object {
    public const val INTERFACE_NAME: String = "org.freedesktop.two.DBus.Properties"
  }
}
