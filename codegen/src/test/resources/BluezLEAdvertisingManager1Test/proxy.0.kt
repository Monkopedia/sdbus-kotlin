package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class LEAdvertisingManager1Proxy(
  public val proxy: Proxy,
) : LEAdvertisingManager1 {
  override val activeInstances: UByte by proxy.prop(LEAdvertisingManager1.Companion.INTERFACE_NAME,
      PropertyName("ActiveInstances")) 

  override val supportedInstances: UByte by
      proxy.prop(LEAdvertisingManager1.Companion.INTERFACE_NAME, PropertyName("SupportedInstances"))
      

  override val supportedIncludes: List<String> by
      proxy.prop(LEAdvertisingManager1.Companion.INTERFACE_NAME, PropertyName("SupportedIncludes")) 

  public override fun register() {
  }

  override suspend fun registerAdvertisement(advertisement: ObjectPath,
      options: Map<String, Variant>): Unit =
      proxy.callMethodAsync(LEAdvertisingManager1.Companion.INTERFACE_NAME,
      MethodName("RegisterAdvertisement")) {
    call(advertisement, options)
  }

  override suspend fun unregisterAdvertisement(service: ObjectPath): Unit =
      proxy.callMethodAsync(LEAdvertisingManager1.Companion.INTERFACE_NAME,
      MethodName("UnregisterAdvertisement")) {
    call(service)
  }
}
