package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class Device1Adaptor(
  protected val obj: IObject,
) : Device1 {
  public override fun register() {
    obj.addVTable(Device1.Companion.INTERFACE_NAME) {
      method("Connect") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@Device1Adaptor::connect)
      }
      method("Disconnect") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@Device1Adaptor::disconnect)
      }
      method("ConnectProfile") {
        inputParamNames = listOf("uuid")
        outputParamNames = listOf()
        acall(this@Device1Adaptor::connectProfile)
      }
      method("DisconnectProfile") {
        inputParamNames = listOf("uuid")
        outputParamNames = listOf()
        acall(this@Device1Adaptor::disconnectProfile)
      }
      method("Pair") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@Device1Adaptor::pair)
      }
      method("CancelPairing") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@Device1Adaptor::cancelPairing)
      }
      prop("Address") {
        with(this@Device1Adaptor::address)
      }
      prop("AddressType") {
        with(this@Device1Adaptor::addressType)
      }
      prop("Name") {
        with(this@Device1Adaptor::name)
      }
      prop("Icon") {
        with(this@Device1Adaptor::icon)
      }
      prop("Class") {
        with(this@Device1Adaptor::`class`)
      }
      prop("Appearance") {
        with(this@Device1Adaptor::appearance)
      }
      prop("UUIDs") {
        with(this@Device1Adaptor::uUIDs)
      }
      prop("Paired") {
        with(this@Device1Adaptor::paired)
      }
      prop("Connected") {
        with(this@Device1Adaptor::connected)
      }
      prop("Trusted") {
        with(this@Device1Adaptor::trusted)
      }
      prop("Blocked") {
        with(this@Device1Adaptor::blocked)
      }
      prop("WakeAllowed") {
        with(this@Device1Adaptor::wakeAllowed)
      }
      prop("Alias") {
        with(this@Device1Adaptor::alias)
      }
      prop("Adapter") {
        with(this@Device1Adaptor::adapter)
      }
      prop("LegacyPairing") {
        with(this@Device1Adaptor::legacyPairing)
      }
      prop("Modalias") {
        with(this@Device1Adaptor::modalias)
      }
      prop("RSSI") {
        with(this@Device1Adaptor::rSSI)
      }
      prop("TxPower") {
        with(this@Device1Adaptor::txPower)
      }
      prop("ManufacturerData") {
        with(this@Device1Adaptor::manufacturerData)
      }
      prop("ServiceData") {
        with(this@Device1Adaptor::serviceData)
      }
      prop("ServicesResolved") {
        with(this@Device1Adaptor::servicesResolved)
      }
      prop("AdvertisingFlags") {
        with(this@Device1Adaptor::advertisingFlags)
      }
      prop("AdvertisingData") {
        with(this@Device1Adaptor::advertisingData)
      }
    }
  }
}
