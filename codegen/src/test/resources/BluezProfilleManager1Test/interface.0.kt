package org.bluez

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.collections.Map

public interface ProfileManager1 {
  public fun register()

  public suspend fun registerProfile(
    profile: ObjectPath,
    uUID: String,
    options: Map<String, Variant>,
  )

  public suspend fun unregisterProfile(profile: ObjectPath)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.ProfileManager1")
  }
}
