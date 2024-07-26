package org.bluez

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.UByte
import kotlin.collections.List
import kotlin.collections.Map

public interface LEAdvertisingManager1 {
  public val activeInstances: UByte

  public val supportedInstances: UByte

  public val supportedIncludes: List<String>

  public fun register()

  public suspend fun registerAdvertisement(advertisement: ObjectPath, options: Map<String, Variant>)

  public suspend fun unregisterAdvertisement(service: ObjectPath)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.LEAdvertisingManager1")
  }
}
