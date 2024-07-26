package org.bluez

import com.monkopedia.sdbus.Variant
import kotlin.Boolean
import kotlin.String
import kotlin.UByte
import kotlin.UShort
import kotlin.collections.List
import kotlin.collections.Map

public interface LEAdvertisement1 {
  public val type: String

  public val serviceUUIDs: List<String>

  public val serviceData: Map<String, Variant>

  public val manufacturerData: Map<UShort, Variant>

  public val `data`: Map<UByte, Variant>

  public val discoverable: Boolean

  public val discoverableTimeout: UShort

  public val includes: List<String>

  public val localName: String

  public val appearance: UShort

  public val duration: UShort

  public val timeout: UShort

  public fun register()

  public suspend fun release()

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.LEAdvertisement1"
  }
}
