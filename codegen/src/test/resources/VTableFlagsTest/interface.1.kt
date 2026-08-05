package org.example.flags

import com.monkopedia.sdbus.InterfaceName
import kotlin.String

public interface Quiet {
  public val inherited: String

  public val overridden: String

  public fun register()

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.example.flags.Quiet")
  }
}
