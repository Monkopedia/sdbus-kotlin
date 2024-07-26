package org.bluez

import kotlin.String

public interface NetworkServer1 {
  public fun register()

  public suspend fun register(uuid: String, bridge: String)

  public suspend fun unregister(uuid: String)

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.NetworkServer1"
  }
}
