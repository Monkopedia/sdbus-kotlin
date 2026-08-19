package org.example

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import com.monkopedia.sdbus.signalFlow
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlinx.coroutines.flow.Flow

public class NamingProxy(
  public val proxy: Proxy,
) : Naming {
  public val geometryProperty: PropertyDelegate<NamingProxy, Geometry> =
      proxy.propDelegate(Naming.Companion.INTERFACE_NAME, PropertyName("Geometry")) 

  public val metadataProperty: PropertyDelegate<NamingProxy, Map<String, Variant>> =
      proxy.propDelegate(Naming.Companion.INTERFACE_NAME, PropertyName("Metadata")) 

  override val geometry: Geometry by geometryProperty

  override val metadata: Map<String, Variant> by metadataProperty

  public val resized: Flow<Size> =
      proxy.signalFlow(Naming.Companion.INTERFACE_NAME, SignalName("Resized")) {
        call { a: Size -> a }
      }

  public override fun register() {
  }

  override suspend fun transform(origin: OriginType): List<OriginType> = proxy.callMethodAsync(Naming.Companion.INTERFACE_NAME, MethodName("Transform")) {
    call(origin)
  }

  override suspend fun describe(spec: Spec): String = proxy.callMethodAsync(Naming.Companion.INTERFACE_NAME, MethodName("Describe")) {
    call(spec)
  }

  override suspend fun bounds(): Extent = proxy.callMethodAsync(Naming.Companion.INTERFACE_NAME, MethodName("Bounds")) {
    call()
  }
}
