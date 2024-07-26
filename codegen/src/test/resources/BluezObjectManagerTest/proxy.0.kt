package org.freedesktop.DBus

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class ObjectManagerProxy(
  protected val proxy: IProxy,
) : ObjectManager {
  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun getManagedObjects(): Map<ObjectPath, Map<String, Map<String, Variant>>> =
        proxy.callMethodAsync(ObjectManager.Companion.INTERFACE_NAME, "GetManagedObjects") { call()
        }
}