package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class GattManager1Adaptor(
  protected val obj: IObject,
) : GattManager1 {
  public override fun register() {
    obj.addVTable(GattManager1.Companion.INTERFACE_NAME) {
      method("RegisterApplication") {
        inputParamNames = listOf("application", "options")
        outputParamNames = listOf()
        acall(this@GattManager1Adaptor::registerApplication)
      }
      method("UnregisterApplication") {
        inputParamNames = listOf("application")
        outputParamNames = listOf()
        acall(this@GattManager1Adaptor::unregisterApplication)
      }
    }
  }
}
