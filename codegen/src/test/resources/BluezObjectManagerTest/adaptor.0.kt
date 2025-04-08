package org.freedesktop.dbus

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class ObjectManagerAdaptor(
  public val obj: Object,
) : ObjectManager {
  public override fun register() {
    obj.addVTable(ObjectManager.Companion.INTERFACE_NAME) {
      method(MethodName("GetManagedObjects")) {
        outputParamNames = listOf("value")
        acall(this@ObjectManagerAdaptor::getManagedObjects)
      }
    }
  }
}
