package org.bluez

import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.prop

public abstract class GattService1Adaptor(
  public val obj: Object,
) : GattService1 {
  public override fun register() {
    obj.addVTable(GattService1.Companion.INTERFACE_NAME) {
      prop(PropertyName("UUID")) {
        with(this@GattService1Adaptor::uUID)
      }
      prop(PropertyName("Primary")) {
        with(this@GattService1Adaptor::primary)
      }
      prop(PropertyName("Includes")) {
        with(this@GattService1Adaptor::includes)
      }
    }
  }
}
