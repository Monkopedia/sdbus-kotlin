package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class Adapter1Adaptor(
  protected val obj: IObject,
) : Adapter1 {
  public override fun register() {
    obj.addVTable(Adapter1.Companion.INTERFACE_NAME) {
      method("StartDiscovery") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@Adapter1Adaptor::startDiscovery)
      }
      method("SetDiscoveryFilter") {
        inputParamNames = listOf("properties")
        outputParamNames = listOf()
        acall(this@Adapter1Adaptor::setDiscoveryFilter)
      }
      method("StopDiscovery") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@Adapter1Adaptor::stopDiscovery)
      }
      method("RemoveDevice") {
        inputParamNames = listOf("device")
        outputParamNames = listOf()
        acall(this@Adapter1Adaptor::removeDevice)
      }
      method("GetDiscoveryFilters") {
        inputParamNames = listOf()
        outputParamNames = listOf("filters")
        acall(this@Adapter1Adaptor::getDiscoveryFilters)
      }
      method("ConnectDevice") {
        inputParamNames = listOf("properties")
        outputParamNames = listOf()
        acall(this@Adapter1Adaptor::connectDevice)
      }
      prop("Address") {
        with(this@Adapter1Adaptor::address)
      }
      prop("AddressType") {
        with(this@Adapter1Adaptor::addressType)
      }
      prop("Name") {
        with(this@Adapter1Adaptor::name)
      }
      prop("Alias") {
        with(this@Adapter1Adaptor::alias)
      }
      prop("Class") {
        with(this@Adapter1Adaptor::`class`)
      }
      prop("Powered") {
        with(this@Adapter1Adaptor::powered)
      }
      prop("Discoverable") {
        with(this@Adapter1Adaptor::discoverable)
      }
      prop("DiscoverableTimeout") {
        with(this@Adapter1Adaptor::discoverableTimeout)
      }
      prop("Pairable") {
        with(this@Adapter1Adaptor::pairable)
      }
      prop("PairableTimeout") {
        with(this@Adapter1Adaptor::pairableTimeout)
      }
      prop("Discovering") {
        with(this@Adapter1Adaptor::discovering)
      }
      prop("UUIDs") {
        with(this@Adapter1Adaptor::uUIDs)
      }
      prop("Modalias") {
        with(this@Adapter1Adaptor::modalias)
      }
    }
  }
}
