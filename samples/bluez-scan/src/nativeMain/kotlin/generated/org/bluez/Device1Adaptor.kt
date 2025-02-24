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

public abstract class Device1Adaptor(
  public val obj: Object,
) : Device1 {
  public override fun register() {
    obj.addVTable(Device1.Companion.INTERFACE_NAME) {
      method(MethodName("Connect")) {
        acall(this@Device1Adaptor::connect)
      }
      method(MethodName("Disconnect")) {
        acall(this@Device1Adaptor::disconnect)
      }
      method(MethodName("ConnectProfile")) {
        inputParamNames = listOf("uuid")
        acall(this@Device1Adaptor::connectProfile)
      }
      method(MethodName("DisconnectProfile")) {
        inputParamNames = listOf("uuid")
        acall(this@Device1Adaptor::disconnectProfile)
      }
      method(MethodName("Pair")) {
        acall(this@Device1Adaptor::pair)
      }
      method(MethodName("CancelPairing")) {
        acall(this@Device1Adaptor::cancelPairing)
      }
      prop(PropertyName("Address")) {
        with(this@Device1Adaptor::address)
      }
      prop(PropertyName("AddressType")) {
        with(this@Device1Adaptor::addressType)
      }
      prop(PropertyName("Name")) {
        with(this@Device1Adaptor::name)
      }
      prop(PropertyName("Icon")) {
        with(this@Device1Adaptor::icon)
      }
      prop(PropertyName("Class")) {
        with(this@Device1Adaptor::`class`)
      }
      prop(PropertyName("Appearance")) {
        with(this@Device1Adaptor::appearance)
      }
      prop(PropertyName("UUIDs")) {
        with(this@Device1Adaptor::uUIDs)
      }
      prop(PropertyName("Paired")) {
        with(this@Device1Adaptor::paired)
      }
      prop(PropertyName("Connected")) {
        with(this@Device1Adaptor::connected)
      }
      prop(PropertyName("Trusted")) {
        with(this@Device1Adaptor::trusted)
      }
      prop(PropertyName("Blocked")) {
        with(this@Device1Adaptor::blocked)
      }
      prop(PropertyName("WakeAllowed")) {
        with(this@Device1Adaptor::wakeAllowed)
      }
      prop(PropertyName("Alias")) {
        with(this@Device1Adaptor::alias)
      }
      prop(PropertyName("Adapter")) {
        with(this@Device1Adaptor::adapter)
      }
      prop(PropertyName("LegacyPairing")) {
        with(this@Device1Adaptor::legacyPairing)
      }
      prop(PropertyName("Modalias")) {
        with(this@Device1Adaptor::modalias)
      }
      prop(PropertyName("RSSI")) {
        with(this@Device1Adaptor::rSSI)
      }
      prop(PropertyName("TxPower")) {
        with(this@Device1Adaptor::txPower)
      }
      prop(PropertyName("ManufacturerData")) {
        with(this@Device1Adaptor::manufacturerData)
      }
      prop(PropertyName("ServiceData")) {
        with(this@Device1Adaptor::serviceData)
      }
      prop(PropertyName("ServicesResolved")) {
        with(this@Device1Adaptor::servicesResolved)
      }
      prop(PropertyName("AdvertisingFlags")) {
        with(this@Device1Adaptor::advertisingFlags)
      }
      prop(PropertyName("AdvertisingData")) {
        with(this@Device1Adaptor::advertisingData)
      }
    }
  }
}
