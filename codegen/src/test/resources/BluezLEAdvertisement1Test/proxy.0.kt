package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
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
  public val typeProperty: PropertyDelegate<LEAdvertisement1Proxy, String> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Type")) 

  public val serviceUUIDsProperty: PropertyDelegate<LEAdvertisement1Proxy, List<String>> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("ServiceUUIDs")) 

  public val serviceDataProperty: PropertyDelegate<LEAdvertisement1Proxy, Map<String, Variant>> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("ServiceData")) 

  public val manufacturerDataProperty: PropertyDelegate<LEAdvertisement1Proxy, Map<UShort, Variant>>
      =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("ManufacturerData")) 

  public val dataProperty: PropertyDelegate<LEAdvertisement1Proxy, Map<UByte, Variant>> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Data")) 

  public val discoverableProperty: PropertyDelegate<LEAdvertisement1Proxy, Boolean> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Discoverable")) 

  public val discoverableTimeoutProperty: PropertyDelegate<LEAdvertisement1Proxy, UShort> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("DiscoverableTimeout")) 

  public val includesProperty: PropertyDelegate<LEAdvertisement1Proxy, List<String>> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Includes")) 

  public val localNameProperty: PropertyDelegate<LEAdvertisement1Proxy, String> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("LocalName")) 

  public val appearanceProperty: PropertyDelegate<LEAdvertisement1Proxy, UShort> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Appearance")) 

  public val durationProperty: PropertyDelegate<LEAdvertisement1Proxy, UShort> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Duration")) 

  public val timeoutProperty: PropertyDelegate<LEAdvertisement1Proxy, UShort> =
      proxy.propDelegate(LEAdvertisement1.Companion.INTERFACE_NAME, PropertyName("Timeout")) 

  override val type: String by typeProperty

  override val serviceUUIDs: List<String> by serviceUUIDsProperty

  override val serviceData: Map<String, Variant> by serviceDataProperty

  override val manufacturerData: Map<UShort, Variant> by manufacturerDataProperty

  override val `data`: Map<UByte, Variant> by dataProperty

  override val discoverable: Boolean by discoverableProperty

  override val discoverableTimeout: UShort by discoverableTimeoutProperty

  override val includes: List<String> by includesProperty

  override val localName: String by localNameProperty

  override val appearance: UShort by appearanceProperty

  override val duration: UShort by durationProperty

  override val timeout: UShort by timeoutProperty

  public override fun register() {
  }

  override suspend fun release(): Unit = proxy.callMethodAsync(LEAdvertisement1.Companion.INTERFACE_NAME, MethodName("Release")) {
    call()
  }
}
