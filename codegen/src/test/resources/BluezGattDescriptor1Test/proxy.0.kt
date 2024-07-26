package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class GattDescriptor1Proxy(
  protected val proxy: IProxy,
) : GattDescriptor1 {
  override val uUID: String by proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, "UUID") 

  override val characteristic: ObjectPath by proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME,
      "Characteristic") 

  override val `value`: List<UByte> by proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, "Value")
      

  override val flags: List<String> by proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, "Flags") 

  public override fun register() {
  }

  override suspend fun readValue(options: Map<String, Variant>): List<UByte> =
        proxy.callMethodAsync(GattDescriptor1.Companion.INTERFACE_NAME, "ReadValue") { call(options)
        }

  override suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>): Unit =
        proxy.callMethodAsync(GattDescriptor1.Companion.INTERFACE_NAME, "WriteValue") { call(value,
        options) }
}
