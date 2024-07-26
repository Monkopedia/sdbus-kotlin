package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class SimAccess1Proxy(
  protected val proxy: IProxy,
) : SimAccess1 {
  override val connected: Boolean by proxy.prop(SimAccess1.Companion.INTERFACE_NAME, "Connected") 

  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun disconnect(): Unit =
        proxy.callMethodAsync(SimAccess1.Companion.INTERFACE_NAME, "Disconnect") { call() }
}
