package org.bluez

import com.monkopedia.sdbus.ObjectPath
import kotlin.String

public interface AgentManager1 {
  public fun register()

  public suspend fun registerAgent(agent: ObjectPath, capability: String)

  public suspend fun unregisterAgent(agent: ObjectPath)

  public suspend fun requestDefaultAgent(agent: ObjectPath)

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.AgentManager1"
  }
}
