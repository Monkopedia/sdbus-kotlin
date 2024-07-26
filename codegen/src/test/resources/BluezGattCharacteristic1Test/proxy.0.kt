package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.String
import kotlin.UByte
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class GattCharacteristic1Proxy(
  protected val proxy: IProxy,
) : GattCharacteristic1 {
  override val uUID: String by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, "UUID") 

  override val service: ObjectPath by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME,
      "Service") 

  override val `value`: List<UByte> by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME,
      "Value") 

  override val directedValue: Map<UnixFd, List<UByte>> by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, "DirectedValue") 

  override val notifying: Boolean by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME,
      "Notifying") 

  override val flags: List<String> by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME,
      "Flags") 

  override val descriptors: List<ObjectPath> by
      proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME, "Descriptors") 

  override val writeAcquired: Boolean by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME,
      "WriteAcquired") 

  override val notifyAcquired: Boolean by proxy.prop(GattCharacteristic1.Companion.INTERFACE_NAME,
      "NotifyAcquired") 

  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun readValue(options: Map<String, Variant>): List<UByte> =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "ReadValue") {
        call(options) }

  override suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>): Unit =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "WriteValue") {
        call(value, options) }

  override suspend fun acquireWrite(options: Map<String, Variant>): AcquireType =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "AcquireWrite") {
        call(options) }

  override suspend fun acquireNotify(options: Map<String, Variant>): AcquireType =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "AcquireNotify") {
        call(options) }

  override suspend fun startNotify(): Unit =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "StartNotify") { call()
        }

  override suspend fun stopNotify(): Unit =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "StopNotify") { call() }

  override suspend fun confirm(): Unit =
        proxy.callMethodAsync(GattCharacteristic1.Companion.INTERFACE_NAME, "Confirm") { call() }
}
