package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.Short
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.UShort
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class Device1Proxy(
  protected val proxy: IProxy,
) : Device1 {
  override val address: String by proxy.prop(Device1.Companion.INTERFACE_NAME, "Address") 

  override val addressType: String by proxy.prop(Device1.Companion.INTERFACE_NAME, "AddressType") 

  override val name: String by proxy.prop(Device1.Companion.INTERFACE_NAME, "Name") 

  override val icon: String by proxy.prop(Device1.Companion.INTERFACE_NAME, "Icon") 

  override val `class`: UInt by proxy.prop(Device1.Companion.INTERFACE_NAME, "Class") 

  override val appearance: UShort by proxy.prop(Device1.Companion.INTERFACE_NAME, "Appearance") 

  override val uUIDs: List<String> by proxy.prop(Device1.Companion.INTERFACE_NAME, "UUIDs") 

  override val paired: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME, "Paired") 

  override val connected: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME, "Connected") 

  override var trusted: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME, "Trusted") 

  override var blocked: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME, "Blocked") 

  override var wakeAllowed: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME, "WakeAllowed") 

  override var alias: String by proxy.prop(Device1.Companion.INTERFACE_NAME, "Alias") 

  override val adapter: ObjectPath by proxy.prop(Device1.Companion.INTERFACE_NAME, "Adapter") 

  override val legacyPairing: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      "LegacyPairing") 

  override val modalias: String by proxy.prop(Device1.Companion.INTERFACE_NAME, "Modalias") 

  override val rSSI: Short by proxy.prop(Device1.Companion.INTERFACE_NAME, "RSSI") 

  override val txPower: Short by proxy.prop(Device1.Companion.INTERFACE_NAME, "TxPower") 

  override val manufacturerData: Map<UShort, Variant> by
      proxy.prop(Device1.Companion.INTERFACE_NAME, "ManufacturerData") 

  override val serviceData: Map<String, Variant> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      "ServiceData") 

  override val servicesResolved: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      "ServicesResolved") 

  override val advertisingFlags: List<Boolean> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      "AdvertisingFlags") 

  override val advertisingData: Map<UByte, Variant> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      "AdvertisingData") 

  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun connect(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME,
        "Connect") { call() }

  override suspend fun disconnect(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME,
        "Disconnect") { call() }

  override suspend fun connectProfile(uuid: String): Unit =
        proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, "ConnectProfile") { call(uuid) }

  override suspend fun disconnectProfile(uuid: String): Unit =
        proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, "DisconnectProfile") { call(uuid) }

  override suspend fun pair(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME,
        "Pair") { call() }

  override suspend fun cancelPairing(): Unit =
        proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, "CancelPairing") { call() }
}
