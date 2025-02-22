package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map

public class Media1Proxy(
  public val proxy: Proxy,
) : Media1 {
  public override fun register() {
  }

  override suspend fun registerEndpoint(endpoint: ObjectPath, properties: Map<String, Variant>): Unit = proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, MethodName("RegisterEndpoint")) {
    call(endpoint, properties)
  }

  override suspend fun unregisterEndpoint(endpoint: ObjectPath): Unit = proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, MethodName("UnregisterEndpoint")) {
    call(endpoint)
  }

  override suspend fun registerPlayer(player: ObjectPath, properties: Map<String, Variant>): Unit = proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, MethodName("RegisterPlayer")) {
    call(player, properties)
  }

  override suspend fun unregisterPlayer(player: ObjectPath): Unit = proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, MethodName("UnregisterPlayer")) {
    call(player)
  }
}
