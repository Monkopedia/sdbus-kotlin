package org.freedesktop.DBus

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.collections.Map

public class ObjectManagerProxy(
  public val proxy: Proxy,
) : ObjectManager {
  public override fun register() {
  }

  override suspend fun getManagedObjects(): Map<ObjectPath, Map<String, Map<String, Variant>>> =
      proxy.callMethodAsync(ObjectManager.Companion.INTERFACE_NAME, MethodName("GetManagedObjects"))
      {
    call()
  }
}
