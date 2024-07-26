package org.bluez

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
    public const val INTERFACE_NAME: String = "org.bluez.ProfileManager1"
  }
}
