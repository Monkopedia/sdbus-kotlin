package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class Media1Adaptor(
  public val obj: Object,
) : Media1 {
  public override fun register() {
    obj.addVTable(Media1.Companion.INTERFACE_NAME) {
      method(MethodName("RegisterEndpoint")) {
        inputParamNames = listOf("endpoint", "properties")
        acall(this@Media1Adaptor::registerEndpoint)
      }
      method(MethodName("UnregisterEndpoint")) {
        inputParamNames = listOf("endpoint")
        acall(this@Media1Adaptor::unregisterEndpoint)
      }
      method(MethodName("RegisterPlayer")) {
        inputParamNames = listOf("player", "properties")
        acall(this@Media1Adaptor::registerPlayer)
      }
      method(MethodName("UnregisterPlayer")) {
        inputParamNames = listOf("player")
        acall(this@Media1Adaptor::unregisterPlayer)
      }
    }
  }
}
