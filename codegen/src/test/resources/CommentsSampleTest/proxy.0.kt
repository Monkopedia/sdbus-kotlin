package org.freedesktop.dbus

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.signalFlow
import kotlin.String
import kotlinx.coroutines.flow.Flow

public class PropertiesProxy(
  public val proxy: Proxy,
) : Properties {
  public val propertiesChanged: Flow<PropertiesChanged> =
      proxy.signalFlow(Properties.Companion.INTERFACE_NAME, SignalName("PropertiesChanged")) {
        call(::PropertiesChanged)
      }

  public override fun register() {
  }

  override suspend fun `get`(interfaceName: String, propertyName: String): Variant = proxy.callMethodAsync(Properties.Companion.INTERFACE_NAME, MethodName("Get")) {
    call(interfaceName, propertyName)
  }
}
