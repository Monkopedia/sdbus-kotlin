package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class GattDescriptor1Proxy(
  public val proxy: Proxy,
) : GattDescriptor1 {
  override val uUID: String by
      proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("UUID")) 

  override val characteristic: ObjectPath by
      proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("Characteristic")) 

  override val `value`: List<UByte> by
      proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("Value")) 

  override val flags: List<String> by
      proxy.prop(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("Flags")) 

  public override fun register() {
  }

  override suspend fun readValue(options: Map<String, Variant>): List<UByte> = proxy.callMethodAsync(GattDescriptor1.Companion.INTERFACE_NAME, MethodName("ReadValue")) {
    call(options)
  }

  override suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>): Unit = proxy.callMethodAsync(GattDescriptor1.Companion.INTERFACE_NAME, MethodName("WriteValue")) {
    call(value, options)
  }
}
