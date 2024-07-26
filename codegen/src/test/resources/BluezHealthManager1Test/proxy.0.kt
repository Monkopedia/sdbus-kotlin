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
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class HealthManager1Proxy(
  protected val proxy: IProxy,
) : HealthManager1 {
  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun createApplication(config: Map<String, Variant>): ObjectPath =
        proxy.callMethodAsync(HealthManager1.Companion.INTERFACE_NAME, "CreateApplication") {
        call(config) }

  override suspend fun destroyApplication(application: ObjectPath): Unit =
        proxy.callMethodAsync(HealthManager1.Companion.INTERFACE_NAME, "DestroyApplication") {
        call(application) }
}
