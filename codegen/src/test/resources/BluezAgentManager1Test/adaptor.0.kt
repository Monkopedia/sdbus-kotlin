package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class AgentManager1Adaptor(
  protected val obj: IObject,
) : AgentManager1 {
  public override fun register() {
    obj.addVTable(AgentManager1.Companion.INTERFACE_NAME) {
      method("RegisterAgent") {
        inputParamNames = listOf("agent", "capability")
        outputParamNames = listOf()
        acall(this@AgentManager1Adaptor::registerAgent)
      }
      method("UnregisterAgent") {
        inputParamNames = listOf("agent")
        outputParamNames = listOf()
        acall(this@AgentManager1Adaptor::unregisterAgent)
      }
      method("RequestDefaultAgent") {
        inputParamNames = listOf("agent")
        outputParamNames = listOf()
        acall(this@AgentManager1Adaptor::requestDefaultAgent)
      }
    }
  }
}
