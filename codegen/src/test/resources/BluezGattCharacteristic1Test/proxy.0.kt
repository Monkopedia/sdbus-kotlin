package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class GattCharacteristic1Proxy(
  public val proxy: Proxy,
) : GattCharacteristic1 {
  override val uUID: String by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("UUID")) 

  override val service: ObjectPath by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Service")) 

  override val `value`: List<UByte> by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Value")) 

  override val directedValue: Map<UnixFd, List<UByte>> by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("DirectedValue")) 

  override val notifying: Boolean by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Notifying")) 

  override val flags: List<String> by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Flags")) 

  override val descriptors: List<ObjectPath> by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Descriptors")) 

  override val writeAcquired: Boolean by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("WriteAcquired")) 

  override val notifyAcquired: Boolean by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("NotifyAcquired")) 

  public override fun register() {
  }

  override suspend fun readValue(options: Map<String, Variant>): List<UByte> = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("ReadValue")) {
    call(options)
  }

  override suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>): Unit = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("WriteValue")) {
    call(value, options)
  }

  override suspend fun acquireWrite(options: Map<String, Variant>): AcquireType = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("AcquireWrite")) {
    call(options)
  }

  override suspend fun acquireNotify(options: Map<String, Variant>): AcquireType = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("AcquireNotify")) {
    call(options)
  }

  override suspend fun startNotify(): Unit = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("StartNotify")) {
    call()
  }

  override suspend fun stopNotify(): Unit = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("StopNotify")) {
    call()
  }

  override suspend fun confirm(): Unit = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("Confirm")) {
    call()
  }
}
