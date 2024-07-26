package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.Unit

public class AgentManager1Proxy(
  public val proxy: Proxy,
) : AgentManager1 {
  public override fun register() {
  }

  override suspend fun registerAgent(agent: ObjectPath, capability: String): Unit =
      proxy.callMethodAsync(AgentManager1.Companion.INTERFACE_NAME, MethodName("RegisterAgent")) {
    call(agent, capability)
  }

  override suspend fun unregisterAgent(agent: ObjectPath): Unit =
      proxy.callMethodAsync(AgentManager1.Companion.INTERFACE_NAME, MethodName("UnregisterAgent")) {
    call(agent)
  }

  override suspend fun requestDefaultAgent(agent: ObjectPath): Unit =
      proxy.callMethodAsync(AgentManager1.Companion.INTERFACE_NAME,
      MethodName("RequestDefaultAgent")) {
    call(agent)
  }
}
