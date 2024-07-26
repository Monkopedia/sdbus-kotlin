package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class LEAdvertisingManager1Proxy(
  protected val proxy: IProxy,
) : LEAdvertisingManager1 {
  override val activeInstances: UByte by proxy.prop(LEAdvertisingManager1.Companion.INTERFACE_NAME,
      "ActiveInstances") 

  override val supportedInstances: UByte by
      proxy.prop(LEAdvertisingManager1.Companion.INTERFACE_NAME, "SupportedInstances") 

  override val supportedIncludes: List<String> by
      proxy.prop(LEAdvertisingManager1.Companion.INTERFACE_NAME, "SupportedIncludes") 

  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun registerAdvertisement(advertisement: ObjectPath,
        options: Map<String, Variant>): Unit =
        proxy.callMethodAsync(LEAdvertisingManager1.Companion.INTERFACE_NAME,
        "RegisterAdvertisement") { call(advertisement, options) }

  override suspend fun unregisterAdvertisement(service: ObjectPath): Unit =
        proxy.callMethodAsync(LEAdvertisingManager1.Companion.INTERFACE_NAME,
        "UnregisterAdvertisement") { call(service) }
}
