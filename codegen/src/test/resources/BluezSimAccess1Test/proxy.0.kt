package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import kotlin.Boolean
import kotlin.Unit

public class SimAccess1Proxy(
  public val proxy: Proxy,
) : SimAccess1 {
  public val connectedProperty: PropertyDelegate<SimAccess1Proxy, Boolean> =
      proxy.propDelegate(SimAccess1.Companion.INTERFACE_NAME, PropertyName("Connected")) 

  override val connected: Boolean by connectedProperty

  public override fun register() {
  }

  override suspend fun disconnect(): Unit = proxy.callMethodAsync(SimAccess1.Companion.INTERFACE_NAME, MethodName("Disconnect")) {
    call()
  }
}
