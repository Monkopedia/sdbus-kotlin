package org.example.flags

import com.monkopedia.sdbus.InterfaceName
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt

public interface Legacy {
  public val name: String

  public val oldName: String

  public val machineId: String

  public var counter: UInt

  public var silent: Boolean

  public fun register()

  public suspend fun ping(message: String): String

  public suspend fun oldPing(message: String): String

  public suspend fun fireAndForget(`value`: Int)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.example.flags.Legacy")
  }
}
