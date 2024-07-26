package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.String
import kotlin.collections.List
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class GattService1Proxy(
  protected val proxy: IProxy,
) : GattService1 {
  override val uUID: String by proxy.prop(GattService1.Companion.INTERFACE_NAME, "UUID") 

  override val primary: Boolean by proxy.prop(GattService1.Companion.INTERFACE_NAME, "Primary") 

  override val includes: List<ObjectPath> by proxy.prop(GattService1.Companion.INTERFACE_NAME,
      "Includes") 

  public override fun register() {
  }
}
