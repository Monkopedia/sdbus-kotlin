package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import kotlin.Boolean
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class GattCharacteristic1Proxy(
  public val proxy: Proxy,
) : GattCharacteristic1 {
  public val uUIDProperty: PropertyDelegate<GattCharacteristic1Proxy, String> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("UUID")) 

  public val serviceProperty: PropertyDelegate<GattCharacteristic1Proxy, ObjectPath> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Service")) 

  public val valueProperty: PropertyDelegate<GattCharacteristic1Proxy, List<UByte>> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Value")) 

  public val directedValueProperty:
      PropertyDelegate<GattCharacteristic1Proxy, Map<UnixFd, List<UByte>>> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("DirectedValue")) 

  public val notifyingProperty: PropertyDelegate<GattCharacteristic1Proxy, Boolean> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Notifying")) 

  public val flagsProperty: PropertyDelegate<GattCharacteristic1Proxy, List<String>> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Flags")) 

  public val descriptorsProperty: PropertyDelegate<GattCharacteristic1Proxy, List<ObjectPath>> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("Descriptors")) 

  public val writeAcquiredProperty: PropertyDelegate<GattCharacteristic1Proxy, Boolean> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("WriteAcquired")) 

  public val notifyAcquiredProperty: PropertyDelegate<GattCharacteristic1Proxy, Boolean> =
      proxy.propDelegate(GattCharacteristic1.Companion.INTERFACE_NAME, PropertyName("NotifyAcquired")) 

  override val uUID: String by uUIDProperty

  override val service: ObjectPath by serviceProperty

  override val `value`: List<UByte> by valueProperty

  override val directedValue: Map<UnixFd, List<UByte>> by directedValueProperty

  override val notifying: Boolean by notifyingProperty

  override val flags: List<String> by flagsProperty

  override val descriptors: List<ObjectPath> by descriptorsProperty

  override val writeAcquired: Boolean by writeAcquiredProperty

  override val notifyAcquired: Boolean by notifyAcquiredProperty

  public override fun register() {
  }

  override suspend fun readValue(options: Map<String, Variant>): List<UByte> = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("ReadValue")) {
    call(options)
  }

  override suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>): Unit = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("WriteValue")) {
    call(value, options)
  }

  override suspend fun acquireWrite(options: Map<String, Variant>): AcquireType = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("AcquireWrite")) {
    isGroupedReturn = true
    call(options)
  }

  override suspend fun acquireNotify(options: Map<String, Variant>): AcquireType = proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, MethodName("AcquireNotify")) {
    isGroupedReturn = true
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
