package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String
import kotlin.collections.List

public class GattService1Proxy(
  public val proxy: Proxy,
) : GattService1 {
  override val uUID: String by proxy.prop(GattService1.Companion.INTERFACE_NAME,
      PropertyName("UUID")) 

  override val primary: Boolean by proxy.prop(GattService1.Companion.INTERFACE_NAME,
      PropertyName("Primary")) 

  override val includes: List<ObjectPath> by proxy.prop(GattService1.Companion.INTERFACE_NAME,
      PropertyName("Includes")) 

  public override fun register() {
  }
}
