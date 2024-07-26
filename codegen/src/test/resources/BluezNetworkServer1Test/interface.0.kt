package org.bluez

import com.monkopedia.sdbus.InterfaceName
import kotlin.String

public interface NetworkServer1 {
  public fun register()

  public suspend fun register(uuid: String, bridge: String)

  public suspend fun unregister(uuid: String)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.NetworkServer1")
  }
}
