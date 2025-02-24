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

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class Adapter1Proxy(
  public val proxy: Proxy,
) : Adapter1 {
  override val address: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Address")) 

  override val addressType: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("AddressType")) 

  override val name: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, PropertyName("Name")) 

  override var alias: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, PropertyName("Alias"))
      

  override val `class`: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME, PropertyName("Class"))
      

  override var powered: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Powered")) 

  override var discoverable: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Discoverable")) 

  override var discoverableTimeout: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("DiscoverableTimeout")) 

  override var pairable: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Pairable")) 

  override var pairableTimeout: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("PairableTimeout")) 

  override val discovering: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Discovering")) 

  override val uUIDs: List<String> by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("UUIDs")) 

  override val modalias: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Modalias")) 

  public override fun register() {
  }

  override suspend fun startDiscovery(): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("StartDiscovery")) {
    call()
  }

  override suspend fun setDiscoveryFilter(properties: Map<String, Variant>): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("SetDiscoveryFilter")) {
    call(properties)
  }

  override suspend fun stopDiscovery(): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("StopDiscovery")) {
    call()
  }

  override suspend fun removeDevice(device: ObjectPath): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("RemoveDevice")) {
    call(device)
  }

  override suspend fun getDiscoveryFilters(): List<String> =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("GetDiscoveryFilters")) {
    call()
  }

  override suspend fun connectDevice(properties: Map<String, Variant>): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("ConnectDevice")) {
    call(properties)
  }
}
