package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.Unit

public class NetworkServer1Proxy(
  public val proxy: Proxy,
) : NetworkServer1 {
  public override fun register() {
  }

  override suspend fun register(uuid: String, bridge: String): Unit = proxy.callMethodAsync(NetworkServer1.Companion.INTERFACE_NAME, MethodName("Register")) {
    call(uuid, bridge)
  }

  override suspend fun unregister(uuid: String): Unit = proxy.callMethodAsync(NetworkServer1.Companion.INTERFACE_NAME, MethodName("Unregister")) {
    call(uuid)
  }
}
