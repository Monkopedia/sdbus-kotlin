package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.Unit

public class SimAccess1Proxy(
  public val proxy: Proxy,
) : SimAccess1 {
  override val connected: Boolean by
      proxy.prop(SimAccess1.Companion.INTERFACE_NAME, PropertyName("Connected")) 

  public override fun register() {
  }

  override suspend fun disconnect(): Unit = proxy.callMethodAsync(SimAccess1.Companion.INTERFACE_NAME, MethodName("Disconnect")) {
    call()
  }
}
