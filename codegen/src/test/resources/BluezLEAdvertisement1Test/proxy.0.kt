package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String
import kotlin.UByte
import kotlin.UShort
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class LEAdvertisement1Proxy(
  public val proxy: Proxy,
) : LEAdvertisement1 {
  override val type: String by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Type")) 

  override val serviceUUIDs: List<String> by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("ServiceUUIDs")) 

  override val serviceData: Map<String, Variant> by
      proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("ServiceData")) 

  override val manufacturerData: Map<UShort, Variant> by
      proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("ManufacturerData")) 

  override val `data`: Map<UByte, Variant> by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Data")) 

  override val discoverable: Boolean by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Discoverable")) 

  override val discoverableTimeout: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("DiscoverableTimeout")) 

  override val includes: List<String> by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Includes")) 

  override val localName: String by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("LocalName")) 

  override val appearance: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Appearance")) 

  override val duration: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Duration")) 

  override val timeout: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      PropertyName("Timeout")) 

  public override fun register() {
  }

  override suspend fun release(): Unit =
      proxy.callMethodAsync(LEAdvertisement1.Companion.INTERFACE_NAME, MethodName("Release")) {
    call()
  }
}
