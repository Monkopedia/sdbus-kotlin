package org.bluez

import com.monkopedia.sdbus.InterfaceName
import kotlin.Boolean

public interface SimAccess1 {
  public val connected: Boolean

  public fun register()

  public suspend fun disconnect()

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.SimAccess1")
  }
}
