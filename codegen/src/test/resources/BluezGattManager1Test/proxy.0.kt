package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class GattManager1Proxy(
  protected val proxy: IProxy,
) : GattManager1 {
  public override fun register() {
  }

  override suspend fun registerApplication(application: ObjectPath, options: Map<String, Variant>):
        Unit = proxy.callMethodAsync(GattManager1.Companion.INTERFACE_NAME, "RegisterApplication") {
        call(application, options) }

  override suspend fun unregisterApplication(application: ObjectPath): Unit =
        proxy.callMethodAsync(GattManager1.Companion.INTERFACE_NAME, "UnregisterApplication") {
        call(application) }
}
