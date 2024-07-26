package org.freedesktop.DBus

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.signalFlow
import kotlin.OptIn
import kotlin.String
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalNativeApi::class)
public class PropertiesProxy(
  public val proxy: IProxy,
) : Properties {
  public val propertiesChanged: Flow<PropertiesChanged> =
      proxy.signalFlow(Properties.Companion.INTERFACE_NAME, "PropertiesChanged") {
        call(::PropertiesChanged)
      }

  public override fun register() {
  }

  override suspend fun `get`(interfaceName: String, propertyName: String): Variant =
        proxy.callMethodAsync(Properties.Companion.INTERFACE_NAME, "Get") { call(interfaceName,
        propertyName) }
}
