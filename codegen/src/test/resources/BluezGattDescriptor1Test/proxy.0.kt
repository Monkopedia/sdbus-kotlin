package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class GattDescriptor1Proxy(
  public val proxy: Proxy,
) : GattDescriptor1 {
  public val uUIDProperty: PropertyDelegate<GattDescriptor1Proxy, String> =
      proxy.propDelegate(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("UUID")) 

  public val characteristicProperty: PropertyDelegate<GattDescriptor1Proxy, ObjectPath> =
      proxy.propDelegate(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("Characteristic")) 

  public val valueProperty: PropertyDelegate<GattDescriptor1Proxy, List<UByte>> =
      proxy.propDelegate(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("Value")) 

  public val flagsProperty: PropertyDelegate<GattDescriptor1Proxy, List<String>> =
      proxy.propDelegate(GattDescriptor1.Companion.INTERFACE_NAME, PropertyName("Flags")) 

  override val uUID: String by uUIDProperty

  override val characteristic: ObjectPath by characteristicProperty

  override val `value`: List<UByte> by valueProperty

  override val flags: List<String> by flagsProperty

  public override fun register() {
  }

  override suspend fun readValue(options: Map<String, Variant>): List<UByte> = proxy.callMethodAsync(GattDescriptor1.Companion.INTERFACE_NAME, MethodName("ReadValue")) {
    call(options)
  }

  override suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>): Unit = proxy.callMethodAsync(GattDescriptor1.Companion.INTERFACE_NAME, MethodName("WriteValue")) {
    call(`value`, options)
  }
}
