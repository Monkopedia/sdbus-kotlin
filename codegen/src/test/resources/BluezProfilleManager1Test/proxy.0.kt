package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map

public class ProfileManager1Proxy(
  public val proxy: Proxy,
) : ProfileManager1 {
  public override fun register() {
  }

  override suspend fun registerProfile(
    profile: ObjectPath,
    uUID: String,
    options: Map<String, Variant>,
  ): Unit = proxy.callMethodAsync(ProfileManager1.Companion.INTERFACE_NAME,
      MethodName("RegisterProfile")) {
    call(profile, uUID, options)
  }

  override suspend fun unregisterProfile(profile: ObjectPath): Unit =
      proxy.callMethodAsync(ProfileManager1.Companion.INTERFACE_NAME,
      MethodName("UnregisterProfile")) {
    call(profile)
  }
}
