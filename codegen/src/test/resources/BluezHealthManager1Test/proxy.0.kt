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
public class HealthManager1Proxy(
  public val proxy: IProxy,
) : HealthManager1 {
  public override fun register() {
  }

  override suspend fun createApplication(config: Map<String, Variant>): ObjectPath =
        proxy.callMethodAsync(HealthManager1.Companion.INTERFACE_NAME, "CreateApplication") {
        call(config) }

  override suspend fun destroyApplication(application: ObjectPath): Unit =
        proxy.callMethodAsync(HealthManager1.Companion.INTERFACE_NAME, "DestroyApplication") {
        call(application) }
}
