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
