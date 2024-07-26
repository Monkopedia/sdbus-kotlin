package org.bluez

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import kotlin.collections.Map

public interface Adapter1 {
  public val address: String

  public val addressType: String

  public val name: String

  public var alias: String

  public val `class`: UInt

  public var powered: Boolean

  public var discoverable: Boolean

  public var discoverableTimeout: UInt

  public var pairable: Boolean

  public var pairableTimeout: UInt

  public val discovering: Boolean

  public val uUIDs: List<String>

  public val modalias: String

  public fun register()

  public suspend fun startDiscovery()

  public suspend fun setDiscoveryFilter(properties: Map<String, Variant>)

  public suspend fun stopDiscovery()

  public suspend fun removeDevice(device: ObjectPath)

  public suspend fun getDiscoveryFilters(): List<String>

  public suspend fun connectDevice(properties: Map<String, Variant>)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.Adapter1")
  }
}
