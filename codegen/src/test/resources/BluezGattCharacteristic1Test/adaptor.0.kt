package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class GattCharacteristic1Adaptor(
  public val obj: Object,
) : GattCharacteristic1 {
  public override fun register() {
    obj.addVTable(GattCharacteristic1.Companion.INTERFACE_NAME) {
      method(MethodName("ReadValue")) {
        inputParamNames = listOf("options")
        outputParamNames = listOf("value")
        acall(this@GattCharacteristic1Adaptor::readValue)
      }
      method(MethodName("WriteValue")) {
        inputParamNames = listOf("value", "options")
        acall(this@GattCharacteristic1Adaptor::writeValue)
      }
      method(MethodName("AcquireWrite")) {
        inputParamNames = listOf("options")
        outputParamNames = listOf("fd", "mtu")
        acall(this@GattCharacteristic1Adaptor::acquireWrite)
      }
      method(MethodName("AcquireNotify")) {
        inputParamNames = listOf("options")
        outputParamNames = listOf("fd", "mtu")
        acall(this@GattCharacteristic1Adaptor::acquireNotify)
      }
      method(MethodName("StartNotify")) {
        acall(this@GattCharacteristic1Adaptor::startNotify)
      }
      method(MethodName("StopNotify")) {
        acall(this@GattCharacteristic1Adaptor::stopNotify)
      }
      method(MethodName("Confirm")) {
        acall(this@GattCharacteristic1Adaptor::confirm)
      }
      prop(PropertyName("UUID")) {
        with(this@GattCharacteristic1Adaptor::uUID)
      }
      prop(PropertyName("Service")) {
        with(this@GattCharacteristic1Adaptor::service)
      }
      prop(PropertyName("Value")) {
        with(this@GattCharacteristic1Adaptor::`value`)
      }
      prop(PropertyName("DirectedValue")) {
        with(this@GattCharacteristic1Adaptor::directedValue)
      }
      prop(PropertyName("Notifying")) {
        with(this@GattCharacteristic1Adaptor::notifying)
      }
      prop(PropertyName("Flags")) {
        with(this@GattCharacteristic1Adaptor::flags)
      }
      prop(PropertyName("Descriptors")) {
        with(this@GattCharacteristic1Adaptor::descriptors)
      }
      prop(PropertyName("WriteAcquired")) {
        with(this@GattCharacteristic1Adaptor::writeAcquired)
      }
      prop(PropertyName("NotifyAcquired")) {
        with(this@GattCharacteristic1Adaptor::notifyAcquired)
      }
    }
  }
}
