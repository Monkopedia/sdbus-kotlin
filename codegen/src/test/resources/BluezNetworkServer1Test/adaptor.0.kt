package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class NetworkServer1Adaptor(
  public val obj: Object,
) : NetworkServer1 {
  public override fun register() {
    obj.addVTable(NetworkServer1.Companion.INTERFACE_NAME) {
      method(MethodName("Register")) {
        inputParamNames = listOf("uuid", "bridge")
        acall(this@NetworkServer1Adaptor::register)
      }
      method(MethodName("Unregister")) {
        inputParamNames = listOf("uuid")
        acall(this@NetworkServer1Adaptor::unregister)
      }
    }
  }
}
