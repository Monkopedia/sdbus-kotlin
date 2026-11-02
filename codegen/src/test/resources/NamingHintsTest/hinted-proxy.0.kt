package org.example

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import com.monkopedia.sdbus.signalFlow
import kotlin.String
import kotlinx.coroutines.flow.Flow

public class NamingProxy(
  public val proxy: Proxy,
) : Naming {
  public val geometryProperty: PropertyDelegate<NamingProxy, QRect> =
      proxy.propDelegate(Naming.Companion.INTERFACE_NAME, PropertyName("Geometry")) 

  public val metadataProperty: PropertyDelegate<NamingProxy, QVariantMap> =
      proxy.propDelegate(Naming.Companion.INTERFACE_NAME, PropertyName("Metadata")) 

  override val geometry: QRect by geometryProperty

  override val metadata: QVariantMap by metadataProperty

  public val resized: Flow<QSize> =
      proxy.signalFlow(Naming.Companion.INTERFACE_NAME, SignalName("Resized")) {
        call { a: QSize -> a }
      }

  public override fun register() {
  }

  override suspend fun transform(origin: QPointF): QPolygonF = proxy.callMethodAsync(Naming.Companion.INTERFACE_NAME, MethodName("Transform")) {
    call(origin)
  }

  override suspend fun describe(spec: QVersionSpec): String = proxy.callMethodAsync(Naming.Companion.INTERFACE_NAME, MethodName("Describe")) {
    call(spec)
  }

  override suspend fun bounds(): Extent = proxy.callMethodAsync(Naming.Companion.INTERFACE_NAME, MethodName("Bounds")) {
    call()
  }
}
