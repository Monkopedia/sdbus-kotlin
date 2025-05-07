package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.propDelegate
import kotlin.Boolean
import kotlin.String
import kotlin.collections.List

public class GattService1Proxy(
  public val proxy: Proxy,
) : GattService1 {
  public val uUIDProperty: PropertyDelegate<GattService1Proxy, String> =
      proxy.propDelegate(GattService1.Companion.INTERFACE_NAME, PropertyName("UUID")) 

  public val primaryProperty: PropertyDelegate<GattService1Proxy, Boolean> =
      proxy.propDelegate(GattService1.Companion.INTERFACE_NAME, PropertyName("Primary")) 

  public val includesProperty: PropertyDelegate<GattService1Proxy, List<ObjectPath>> =
      proxy.propDelegate(GattService1.Companion.INTERFACE_NAME, PropertyName("Includes")) 

  override val uUID: String by uUIDProperty

  override val primary: Boolean by primaryProperty

  override val includes: List<ObjectPath> by includesProperty

  public override fun register() {
  }
}
