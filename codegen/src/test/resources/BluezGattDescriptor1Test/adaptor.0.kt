package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class GattDescriptor1Adaptor(
  protected val obj: IObject,
) : GattDescriptor1 {
  public override fun register() {
    obj.addVTable(GattDescriptor1.Companion.INTERFACE_NAME) {
      method("ReadValue") {
        inputParamNames = listOf("options")
        outputParamNames = listOf("value")
        acall(this@GattDescriptor1Adaptor::readValue)
      }
      method("WriteValue") {
        inputParamNames = listOf("value", "options")
        outputParamNames = listOf()
        acall(this@GattDescriptor1Adaptor::writeValue)
      }
      prop("UUID") {
        with(this@GattDescriptor1Adaptor::uUID)
      }
      prop("Characteristic") {
        with(this@GattDescriptor1Adaptor::characteristic)
      }
      prop("Value") {
        with(this@GattDescriptor1Adaptor::`value`)
      }
      prop("Flags") {
        with(this@GattDescriptor1Adaptor::flags)
      }
    }
  }
}
