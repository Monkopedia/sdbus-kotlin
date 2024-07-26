package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map

public class GattManager1Proxy(
  public val proxy: Proxy,
) : GattManager1 {
  public override fun register() {
  }

  override suspend fun registerApplication(application: ObjectPath, options: Map<String, Variant>):
      Unit = proxy.callMethodAsync(GattManager1.Companion.INTERFACE_NAME,
      MethodName("RegisterApplication")) {
    call(application, options)
  }

  override suspend fun unregisterApplication(application: ObjectPath): Unit =
      proxy.callMethodAsync(GattManager1.Companion.INTERFACE_NAME,
      MethodName("UnregisterApplication")) {
    call(application)
  }
}
