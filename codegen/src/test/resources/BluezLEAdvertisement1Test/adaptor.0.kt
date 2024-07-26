package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class LEAdvertisement1Adaptor(
  public val obj: Object,
) : LEAdvertisement1 {
  public override fun register() {
    obj.addVTable(LEAdvertisement1.Companion.INTERFACE_NAME) {
      method(MethodName("Release")) {
        acall(this@LEAdvertisement1Adaptor::release)
      }
      prop(PropertyName("Type")) {
        with(this@LEAdvertisement1Adaptor::type)
      }
      prop(PropertyName("ServiceUUIDs")) {
        with(this@LEAdvertisement1Adaptor::serviceUUIDs)
      }
      prop(PropertyName("ServiceData")) {
        with(this@LEAdvertisement1Adaptor::serviceData)
      }
      prop(PropertyName("ManufacturerData")) {
        with(this@LEAdvertisement1Adaptor::manufacturerData)
      }
      prop(PropertyName("Data")) {
        with(this@LEAdvertisement1Adaptor::`data`)
      }
      prop(PropertyName("Discoverable")) {
        with(this@LEAdvertisement1Adaptor::discoverable)
      }
      prop(PropertyName("DiscoverableTimeout")) {
        with(this@LEAdvertisement1Adaptor::discoverableTimeout)
      }
      prop(PropertyName("Includes")) {
        with(this@LEAdvertisement1Adaptor::includes)
      }
      prop(PropertyName("LocalName")) {
        with(this@LEAdvertisement1Adaptor::localName)
      }
      prop(PropertyName("Appearance")) {
        with(this@LEAdvertisement1Adaptor::appearance)
      }
      prop(PropertyName("Duration")) {
        with(this@LEAdvertisement1Adaptor::duration)
      }
      prop(PropertyName("Timeout")) {
        with(this@LEAdvertisement1Adaptor::timeout)
      }
    }
  }
}
