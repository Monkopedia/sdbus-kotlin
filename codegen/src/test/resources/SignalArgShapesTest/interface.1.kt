package org.example.signals

import com.monkopedia.sdbus.InterfaceName

public interface Shapes {
  public fun register()

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.example.signals.Shapes")
  }
}
