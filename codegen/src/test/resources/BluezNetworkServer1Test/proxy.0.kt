package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class NetworkServer1Proxy(
  protected val proxy: IProxy,
) : NetworkServer1 {
  public override fun register() {
  }

  override suspend fun register(uuid: String, bridge: String): Unit =
        proxy.callMethodAsync(NetworkServer1.Companion.INTERFACE_NAME, "Register") { call(uuid,
        bridge) }

  override suspend fun unregister(uuid: String): Unit =
        proxy.callMethodAsync(NetworkServer1.Companion.INTERFACE_NAME, "Unregister") { call(uuid) }
}
