package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface Media1 {
  public fun register()

  public suspend fun registerEndpoint(endpoint: ObjectPath, properties: Map<String, Variant>)

  public suspend fun unregisterEndpoint(endpoint: ObjectPath)

  public suspend fun registerPlayer(player: ObjectPath, properties: Map<String, Variant>)

  public suspend fun unregisterPlayer(player: ObjectPath)

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.Media1"
  }
}
