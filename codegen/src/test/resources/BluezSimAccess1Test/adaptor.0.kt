package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class SimAccess1Adaptor(
  public val obj: Object,
) : SimAccess1 {
  public override fun register() {
    obj.addVTable(SimAccess1.Companion.INTERFACE_NAME) {
      method(MethodName("Disconnect")) {
        acall(this@SimAccess1Adaptor::disconnect)
      }
      prop(PropertyName("Connected")) {
        with(this@SimAccess1Adaptor::connected)
      }
    }
  }
}
