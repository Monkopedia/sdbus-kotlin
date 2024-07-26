package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class AgentManager1Adaptor(
  public val obj: Object,
) : AgentManager1 {
  public override fun register() {
    obj.addVTable(AgentManager1.Companion.INTERFACE_NAME) {
      method(MethodName("RegisterAgent")) {
        inputParamNames = listOf("agent", "capability")
        acall(this@AgentManager1Adaptor::registerAgent)
      }
      method(MethodName("UnregisterAgent")) {
        inputParamNames = listOf("agent")
        acall(this@AgentManager1Adaptor::unregisterAgent)
      }
      method(MethodName("RequestDefaultAgent")) {
        inputParamNames = listOf("agent")
        acall(this@AgentManager1Adaptor::requestDefaultAgent)
      }
    }
  }
}
