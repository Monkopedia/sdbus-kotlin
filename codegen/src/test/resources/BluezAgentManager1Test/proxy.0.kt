package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class AgentManager1Proxy(
  protected val proxy: IProxy,
) : AgentManager1 {
  public override fun register() {
  }

  override suspend fun registerAgent(agent: ObjectPath, capability: String): Unit =
        proxy.callMethodAsync(AgentManager1.Companion.INTERFACE_NAME, "RegisterAgent") { call(agent,
        capability) }

  override suspend fun unregisterAgent(agent: ObjectPath): Unit =
        proxy.callMethodAsync(AgentManager1.Companion.INTERFACE_NAME, "UnregisterAgent") {
        call(agent) }

  override suspend fun requestDefaultAgent(agent: ObjectPath): Unit =
        proxy.callMethodAsync(AgentManager1.Companion.INTERFACE_NAME, "RequestDefaultAgent") {
        call(agent) }
}
