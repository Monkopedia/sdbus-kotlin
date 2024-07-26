package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class SimAccess1Proxy(
  public val proxy: IProxy,
) : SimAccess1 {
  override val connected: Boolean by proxy.prop(SimAccess1.Companion.INTERFACE_NAME, "Connected") 

  public override fun register() {
  }

  override suspend fun disconnect(): Unit =
        proxy.callMethodAsync(SimAccess1.Companion.INTERFACE_NAME, "Disconnect") { call() }
}
