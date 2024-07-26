package org.freedesktop.DBus

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class ObjectManagerProxy(
  protected val proxy: IProxy,
) : ObjectManager {
  public override fun register() {
  }

  override suspend fun getManagedObjects(): Map<ObjectPath, Map<String, Map<String, Variant>>> =
        proxy.callMethodAsync(ObjectManager.Companion.INTERFACE_NAME, "GetManagedObjects") { call()
        }
}
