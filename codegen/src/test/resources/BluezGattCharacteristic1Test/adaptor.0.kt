package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class GattCharacteristic1Adaptor(
  protected val obj: IObject,
) : GattCharacteristic1 {
  public override fun register() {
    obj.addVTable(GattCharacteristic1.Companion.INTERFACE_NAME) {
      method("ReadValue") {
        inputParamNames = listOf("options")
        outputParamNames = listOf("value")
        acall(this@GattCharacteristic1Adaptor::readValue)
      }
      method("WriteValue") {
        inputParamNames = listOf("value", "options")
        outputParamNames = listOf()
        acall(this@GattCharacteristic1Adaptor::writeValue)
      }
      method("AcquireWrite") {
        inputParamNames = listOf("options")
        outputParamNames = listOf("fd", "mtu")
        acall(this@GattCharacteristic1Adaptor::acquireWrite)
      }
      method("AcquireNotify") {
        inputParamNames = listOf("options")
        outputParamNames = listOf("fd", "mtu")
        acall(this@GattCharacteristic1Adaptor::acquireNotify)
      }
      method("StartNotify") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@GattCharacteristic1Adaptor::startNotify)
      }
      method("StopNotify") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@GattCharacteristic1Adaptor::stopNotify)
      }
      method("Confirm") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@GattCharacteristic1Adaptor::confirm)
      }
      prop("UUID") {
        with(this@GattCharacteristic1Adaptor::uUID)
      }
      prop("Service") {
        with(this@GattCharacteristic1Adaptor::service)
      }
      prop("Value") {
        with(this@GattCharacteristic1Adaptor::`value`)
      }
      prop("DirectedValue") {
        with(this@GattCharacteristic1Adaptor::directedValue)
      }
      prop("Notifying") {
        with(this@GattCharacteristic1Adaptor::notifying)
      }
      prop("Flags") {
        with(this@GattCharacteristic1Adaptor::flags)
      }
      prop("Descriptors") {
        with(this@GattCharacteristic1Adaptor::descriptors)
      }
      prop("WriteAcquired") {
        with(this@GattCharacteristic1Adaptor::writeAcquired)
      }
      prop("NotifyAcquired") {
        with(this@GattCharacteristic1Adaptor::notifyAcquired)
      }
    }
  }
}
