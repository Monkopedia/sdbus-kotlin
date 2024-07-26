package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class ProfileManager1Proxy(
  protected val proxy: IProxy,
) : ProfileManager1 {
  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun registerProfile(
    profile: ObjectPath,
    uUID: String,
    options: Map<String, Variant>,
  ): Unit = proxy.callMethodAsync(ProfileManager1.Companion.INTERFACE_NAME, "RegisterProfile") {
        call(profile, uUID, options) }

  override suspend fun unregisterProfile(profile: ObjectPath): Unit =
        proxy.callMethodAsync(ProfileManager1.Companion.INTERFACE_NAME, "UnregisterProfile") {
        call(profile) }
}
