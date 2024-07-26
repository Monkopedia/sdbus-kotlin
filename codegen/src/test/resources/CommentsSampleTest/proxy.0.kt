package org.freedesktop.DBus

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.onSignal
import kotlin.OptIn
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class PropertiesProxy(
  protected val proxy: IProxy,
) : Properties {
  public override fun register() {
    val weakRef = WeakReference(this)
    proxy.onSignal(Properties.Companion.INTERFACE_NAME, "PropertiesChanged") {
      acall {
          interfaceName: String,
          changedProperties: Map<String, Variant>,
          invalidatedProperties: List<String>,
        ->
        weakRef.get()
          ?.onPropertiesChanged(interfaceName, changedProperties, invalidatedProperties)
          ?: Unit
      }
    }
  }

  override suspend fun `get`(interfaceName: String, propertyName: String): Variant =
        proxy.callMethodAsync(Properties.Companion.INTERFACE_NAME, "Get") { call(interfaceName,
        propertyName) }
}
