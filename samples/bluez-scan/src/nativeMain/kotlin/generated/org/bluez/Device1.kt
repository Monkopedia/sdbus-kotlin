/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
package org.bluez

import com.monkopedia.sdbus.InterfaceName
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
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.Device1")
  }
}
