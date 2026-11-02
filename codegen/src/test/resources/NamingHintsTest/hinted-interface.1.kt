package org.example

import com.monkopedia.sdbus.InterfaceName
import kotlin.String

public interface Naming {
  public val geometry: QRect

  public val metadata: QVariantMap

  public fun register()

  public suspend fun transform(origin: QPointF): QPolygonF

  public suspend fun describe(spec: QVersionSpec): String

  public suspend fun bounds(): Extent

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.example.Naming")
  }
}
