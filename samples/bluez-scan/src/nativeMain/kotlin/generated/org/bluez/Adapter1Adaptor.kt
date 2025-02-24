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
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class Adapter1Adaptor(
  public val obj: Object,
) : Adapter1 {
  public override fun register() {
    obj.addVTable(Adapter1.Companion.INTERFACE_NAME) {
      method(MethodName("StartDiscovery")) {
        acall(this@Adapter1Adaptor::startDiscovery)
      }
      method(MethodName("SetDiscoveryFilter")) {
        inputParamNames = listOf("properties")
        acall(this@Adapter1Adaptor::setDiscoveryFilter)
      }
      method(MethodName("StopDiscovery")) {
        acall(this@Adapter1Adaptor::stopDiscovery)
      }
      method(MethodName("RemoveDevice")) {
        inputParamNames = listOf("device")
        acall(this@Adapter1Adaptor::removeDevice)
      }
      method(MethodName("GetDiscoveryFilters")) {
        outputParamNames = listOf("filters")
        acall(this@Adapter1Adaptor::getDiscoveryFilters)
      }
      method(MethodName("ConnectDevice")) {
        inputParamNames = listOf("properties")
        acall(this@Adapter1Adaptor::connectDevice)
      }
      prop(PropertyName("Address")) {
        with(this@Adapter1Adaptor::address)
      }
      prop(PropertyName("AddressType")) {
        with(this@Adapter1Adaptor::addressType)
      }
      prop(PropertyName("Name")) {
        with(this@Adapter1Adaptor::name)
      }
      prop(PropertyName("Alias")) {
        with(this@Adapter1Adaptor::alias)
      }
      prop(PropertyName("Class")) {
        with(this@Adapter1Adaptor::`class`)
      }
      prop(PropertyName("Powered")) {
        with(this@Adapter1Adaptor::powered)
      }
      prop(PropertyName("Discoverable")) {
        with(this@Adapter1Adaptor::discoverable)
      }
      prop(PropertyName("DiscoverableTimeout")) {
        with(this@Adapter1Adaptor::discoverableTimeout)
      }
      prop(PropertyName("Pairable")) {
        with(this@Adapter1Adaptor::pairable)
      }
      prop(PropertyName("PairableTimeout")) {
        with(this@Adapter1Adaptor::pairableTimeout)
      }
      prop(PropertyName("Discovering")) {
        with(this@Adapter1Adaptor::discovering)
      }
      prop(PropertyName("UUIDs")) {
        with(this@Adapter1Adaptor::uUIDs)
      }
      prop(PropertyName("Modalias")) {
        with(this@Adapter1Adaptor::modalias)
      }
    }
  }
}
