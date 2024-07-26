package org.freedesktop.two.DBus

import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlinx.serialization.Serializable

@Serializable
public data class PropertiesChanged(
  public val interface_name: String,
  public val changed_properties: Map<String, Variant>,
  public val invalidated_properties: List<String>,
)
