package org.bluez

import kotlin.Boolean
import kotlin.String

public interface SimAccess1 {
  public val connected: Boolean

  public fun register()

  public suspend fun disconnect()

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.SimAccess1"
  }
}
