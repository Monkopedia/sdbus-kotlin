package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.notifying
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String

public abstract class Device1Adaptor(
  public val obj: Object,
) : Device1 {
  override var trusted: Boolean by
      obj.notifying(Device1.Companion.INTERFACE_NAME, PropertyName("Trusted"), false)

  override var blocked: Boolean by
      obj.notifying(Device1.Companion.INTERFACE_NAME, PropertyName("Blocked"), false)

  override var wakeAllowed: Boolean by
      obj.notifying(Device1.Companion.INTERFACE_NAME, PropertyName("WakeAllowed"), false)

  override var alias: String by
      obj.notifying(Device1.Companion.INTERFACE_NAME, PropertyName("Alias"), "")

  public override fun register() {
    obj.addVTable(Device1.Companion.INTERFACE_NAME) {
      method(MethodName("Connect")) {
        asyncCall(this@Device1Adaptor::connect)
      }
      method(MethodName("Disconnect")) {
        asyncCall(this@Device1Adaptor::disconnect)
      }
      method(MethodName("ConnectProfile")) {
        inputParamNames = listOf("uuid")
        asyncCall(this@Device1Adaptor::connectProfile)
      }
      method(MethodName("DisconnectProfile")) {
        inputParamNames = listOf("uuid")
        asyncCall(this@Device1Adaptor::disconnectProfile)
      }
      method(MethodName("Pair")) {
        asyncCall(this@Device1Adaptor::pair)
      }
      method(MethodName("CancelPairing")) {
        asyncCall(this@Device1Adaptor::cancelPairing)
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
