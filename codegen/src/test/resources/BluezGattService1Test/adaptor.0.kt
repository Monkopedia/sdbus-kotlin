package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class GattService1Adaptor(
  protected val obj: IObject,
) : GattService1 {
  public override fun register() {
    obj.addVTable(GattService1.Companion.INTERFACE_NAME) {
      prop("UUID") {
        with(this@GattService1Adaptor::uUID)
      }
      prop("Primary") {
        with(this@GattService1Adaptor::primary)
      }
      prop("Includes") {
        with(this@GattService1Adaptor::includes)
      }
    }
  }
}
