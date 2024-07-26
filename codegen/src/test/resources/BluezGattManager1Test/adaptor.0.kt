package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class GattManager1Adaptor(
  public val obj: Object,
) : GattManager1 {
  public override fun register() {
    obj.addVTable(GattManager1.Companion.INTERFACE_NAME) {
      method(MethodName("RegisterApplication")) {
        inputParamNames = listOf("application", "options")
        acall(this@GattManager1Adaptor::registerApplication)
      }
      method(MethodName("UnregisterApplication")) {
        inputParamNames = listOf("application")
        acall(this@GattManager1Adaptor::unregisterApplication)
      }
    }
  }
}
