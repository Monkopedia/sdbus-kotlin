package org.bluez

import com.monkopedia.sdbus.ObjectPath
import kotlin.Boolean
import kotlin.String
import kotlin.collections.List

public interface GattService1 {
  public val uUID: String

  public val primary: Boolean

  public val includes: List<ObjectPath>

  public fun register()

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.GattService1"
  }
}
