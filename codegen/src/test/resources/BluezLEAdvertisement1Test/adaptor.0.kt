package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class LEAdvertisement1Adaptor(
  protected val obj: IObject,
) : LEAdvertisement1 {
  public override fun register() {
    obj.addVTable(LEAdvertisement1.Companion.INTERFACE_NAME) {
      method("Release") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@LEAdvertisement1Adaptor::release)
      }
      prop("Type") {
        with(this@LEAdvertisement1Adaptor::type)
      }
      prop("ServiceUUIDs") {
        with(this@LEAdvertisement1Adaptor::serviceUUIDs)
      }
      prop("ServiceData") {
        with(this@LEAdvertisement1Adaptor::serviceData)
      }
      prop("ManufacturerData") {
        with(this@LEAdvertisement1Adaptor::manufacturerData)
      }
      prop("Data") {
        with(this@LEAdvertisement1Adaptor::`data`)
      }
      prop("Discoverable") {
        with(this@LEAdvertisement1Adaptor::discoverable)
      }
      prop("DiscoverableTimeout") {
        with(this@LEAdvertisement1Adaptor::discoverableTimeout)
      }
      prop("Includes") {
        with(this@LEAdvertisement1Adaptor::includes)
      }
      prop("LocalName") {
        with(this@LEAdvertisement1Adaptor::localName)
      }
      prop("Appearance") {
        with(this@LEAdvertisement1Adaptor::appearance)
      }
      prop("Duration") {
        with(this@LEAdvertisement1Adaptor::duration)
      }
      prop("Timeout") {
        with(this@LEAdvertisement1Adaptor::timeout)
      }
    }
  }
}
