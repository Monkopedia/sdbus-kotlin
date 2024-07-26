package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.String
import kotlin.UByte
import kotlin.UShort
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class LEAdvertisement1Proxy(
  protected val proxy: IProxy,
) : LEAdvertisement1 {
  override val type: String by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, "Type") 

  override val serviceUUIDs: List<String> by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "ServiceUUIDs") 

  override val serviceData: Map<String, Variant> by
      proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, "ServiceData") 

  override val manufacturerData: Map<UShort, Variant> by
      proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, "ManufacturerData") 

  override val `data`: Map<UByte, Variant> by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "Data") 

  override val discoverable: Boolean by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "Discoverable") 

  override val discoverableTimeout: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "DiscoverableTimeout") 

  override val includes: List<String> by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "Includes") 

  override val localName: String by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "LocalName") 

  override val appearance: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME,
      "Appearance") 

  override val duration: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, "Duration")
      

  override val timeout: UShort by proxy.prop(LEAdvertisement1.Companion.INTERFACE_NAME, "Timeout") 

  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun release(): Unit =
        proxy.callMethodAsync(LEAdvertisement1.Companion.INTERFACE_NAME, "Release") { call() }
}
