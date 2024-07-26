package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class SimAccess1Adaptor(
  protected val obj: IObject,
) : SimAccess1 {
  public override fun register() {
    obj.addVTable(SimAccess1.Companion.INTERFACE_NAME) {
      method("Disconnect") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@SimAccess1Adaptor::disconnect)
      }
      prop("Connected") {
        with(this@SimAccess1Adaptor::connected)
      }
    }
  }
}
