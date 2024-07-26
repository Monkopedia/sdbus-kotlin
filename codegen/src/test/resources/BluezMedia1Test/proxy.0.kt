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

@OptIn(ExperimentalNativeApi::class)
public class Media1Proxy(
  public val proxy: IProxy,
) : Media1 {
  public override fun register() {
  }

  override suspend fun registerEndpoint(endpoint: ObjectPath, properties: Map<String, Variant>):
        Unit = proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, "RegisterEndpoint") {
        call(endpoint, properties) }

  override suspend fun unregisterEndpoint(endpoint: ObjectPath): Unit =
        proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, "UnregisterEndpoint") {
        call(endpoint) }

  override suspend fun registerPlayer(player: ObjectPath, properties: Map<String, Variant>): Unit =
        proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, "RegisterPlayer") { call(player,
        properties) }

  override suspend fun unregisterPlayer(player: ObjectPath): Unit =
        proxy.callMethodAsync(Media1.Companion.INTERFACE_NAME, "UnregisterPlayer") { call(player) }
}
