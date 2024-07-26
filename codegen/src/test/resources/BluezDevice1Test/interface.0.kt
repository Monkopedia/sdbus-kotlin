package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.Boolean
import kotlin.Short
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.UShort
import kotlin.collections.List
import kotlin.collections.Map

public interface Device1 {
  public val address: String

  public val addressType: String

  public val name: String

  public val icon: String

  public val `class`: UInt

  public val appearance: UShort

  public val uUIDs: List<String>

  public val paired: Boolean

  public val connected: Boolean

  public var trusted: Boolean

  public var blocked: Boolean

  public var wakeAllowed: Boolean

  public var alias: String

  public val adapter: ObjectPath

  public val legacyPairing: Boolean

  public val modalias: String

  public val rSSI: Short

  public val txPower: Short

  public val manufacturerData: Map<UShort, Variant>

  public val serviceData: Map<String, Variant>

  public val servicesResolved: Boolean

  public val advertisingFlags: List<Boolean>

  public val advertisingData: Map<UByte, Variant>

  public fun register()

  public suspend fun connect()

  public suspend fun disconnect()

  public suspend fun connectProfile(uuid: String)

  public suspend fun disconnectProfile(uuid: String)

  public suspend fun pair()

  public suspend fun cancelPairing()

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.Device1"
  }
}
