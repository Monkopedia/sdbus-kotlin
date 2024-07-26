package org.bluez

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import kotlin.String

public interface AgentManager1 {
  public fun register()

  public suspend fun registerAgent(agent: ObjectPath, capability: String)

  public suspend fun unregisterAgent(agent: ObjectPath)

  public suspend fun requestDefaultAgent(agent: ObjectPath)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.AgentManager1")
  }
}
