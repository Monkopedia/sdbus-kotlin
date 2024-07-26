package org.foo

import com.monkopedia.sdbus.InterfaceName
import kotlin.Boolean
import kotlin.String

public interface Background {
  public fun register()

  public suspend fun refreshBackground()

  public suspend fun currentBackground(): String

  public suspend fun setBackground(name: String): Boolean

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.foo.Background")
  }
}
