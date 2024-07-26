package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class GattDescriptor1Adaptor(
  public val obj: Object,
) : GattDescriptor1 {
  public override fun register() {
    obj.addVTable(GattDescriptor1.Companion.INTERFACE_NAME) {
      method(MethodName("ReadValue")) {
        inputParamNames = listOf("options")
        outputParamNames = listOf("value")
        acall(this@GattDescriptor1Adaptor::readValue)
      }
      method(MethodName("WriteValue")) {
        inputParamNames = listOf("value", "options")
        acall(this@GattDescriptor1Adaptor::writeValue)
      }
      prop(PropertyName("UUID")) {
        with(this@GattDescriptor1Adaptor::uUID)
      }
      prop(PropertyName("Characteristic")) {
        with(this@GattDescriptor1Adaptor::characteristic)
      }
      prop(PropertyName("Value")) {
        with(this@GattDescriptor1Adaptor::`value`)
      }
      prop(PropertyName("Flags")) {
        with(this@GattDescriptor1Adaptor::flags)
      }
    }
  }
}
