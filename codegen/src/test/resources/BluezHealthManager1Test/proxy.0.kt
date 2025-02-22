package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map

public class HealthManager1Proxy(
  public val proxy: Proxy,
) : HealthManager1 {
  public override fun register() {
  }

  override suspend fun createApplication(config: Map<String, Variant>): ObjectPath = proxy.callMethodAsync(HealthManager1.Companion.INTERFACE_NAME, MethodName("CreateApplication")) {
    call(config)
  }

  override suspend fun destroyApplication(application: ObjectPath): Unit = proxy.callMethodAsync(HealthManager1.Companion.INTERFACE_NAME, MethodName("DestroyApplication")) {
    call(application)
  }
}
