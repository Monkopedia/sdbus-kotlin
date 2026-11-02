package org.example

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public interface Naming {
  public val geometry: Geometry

  public val metadata: Map<String, Variant>

  public fun register()

  public suspend fun transform(origin: OriginType): List<OriginType>

  public suspend fun describe(spec: Spec): String

  public suspend fun bounds(): Extent

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.example.Naming")
  }
}
