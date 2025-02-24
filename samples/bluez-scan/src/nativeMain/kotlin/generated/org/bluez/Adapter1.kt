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
