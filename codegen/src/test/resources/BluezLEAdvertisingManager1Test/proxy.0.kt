package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class LEAdvertisingManager1Proxy(
  public val proxy: Proxy,
) : LEAdvertisingManager1 {
  public val activeInstancesProperty: PropertyDelegate<LEAdvertisingManager1Proxy, UByte> =
      proxy.propDelegate(LEAdvertisingManager1.Companion.INTERFACE_NAME, PropertyName("ActiveInstances")) 

  public val supportedInstancesProperty: PropertyDelegate<LEAdvertisingManager1Proxy, UByte> =
      proxy.propDelegate(LEAdvertisingManager1.Companion.INTERFACE_NAME, PropertyName("SupportedInstances")) 

  public val supportedIncludesProperty: PropertyDelegate<LEAdvertisingManager1Proxy, List<String>> =
      proxy.propDelegate(LEAdvertisingManager1.Companion.INTERFACE_NAME, PropertyName("SupportedIncludes")) 

  override val activeInstances: UByte by activeInstancesProperty

  override val supportedInstances: UByte by supportedInstancesProperty

  override val supportedIncludes: List<String> by supportedIncludesProperty

  public override fun register() {
  }

  override suspend fun registerAdvertisement(advertisement: ObjectPath, options: Map<String, Variant>): Unit = proxy.callMethodAsync(LEAdvertisingManager1.Companion.INTERFACE_NAME, MethodName("RegisterAdvertisement")) {
    call(advertisement, options)
  }

  override suspend fun unregisterAdvertisement(service: ObjectPath): Unit = proxy.callMethodAsync(LEAdvertisingManager1.Companion.INTERFACE_NAME, MethodName("UnregisterAdvertisement")) {
    call(service)
  }
}
