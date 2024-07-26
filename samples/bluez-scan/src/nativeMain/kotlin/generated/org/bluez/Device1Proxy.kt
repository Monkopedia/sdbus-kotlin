package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.Short
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.UShort
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class Device1Proxy(
  public val proxy: Proxy,
) : Device1 {
  override val address: String by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Address")) 

  override val addressType: String by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("AddressType")) 

  override val name: String by proxy.prop(Device1.Companion.INTERFACE_NAME, PropertyName("Name")) 

  override val icon: String by proxy.prop(Device1.Companion.INTERFACE_NAME, PropertyName("Icon")) 

  override val `class`: UInt by proxy.prop(Device1.Companion.INTERFACE_NAME, PropertyName("Class")) 

  override val appearance: UShort by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Appearance")) 

  override val uUIDs: List<String> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("UUIDs")) 

  override val paired: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Paired")) 

  override val connected: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Connected")) 

  override var trusted: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Trusted")) 

  override var blocked: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Blocked")) 

  override var wakeAllowed: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("WakeAllowed")) 

  override var alias: String by proxy.prop(Device1.Companion.INTERFACE_NAME, PropertyName("Alias")) 

  override val adapter: ObjectPath by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Adapter")) 

  override val legacyPairing: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("LegacyPairing")) 

  override val modalias: String by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("Modalias")) 

  override val rSSI: Short by proxy.prop(Device1.Companion.INTERFACE_NAME, PropertyName("RSSI")) 

  override val txPower: Short by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("TxPower")) 

  override val manufacturerData: Map<UShort, Variant> by
      proxy.prop(Device1.Companion.INTERFACE_NAME, PropertyName("ManufacturerData")) 

  override val serviceData: Map<String, Variant> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("ServiceData")) 

  override val servicesResolved: Boolean by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("ServicesResolved")) 

  override val advertisingFlags: List<Boolean> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("AdvertisingFlags")) 

  override val advertisingData: Map<UByte, Variant> by proxy.prop(Device1.Companion.INTERFACE_NAME,
      PropertyName("AdvertisingData")) 

  public override fun register() {
  }

  override suspend fun connect(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME,
      MethodName("Connect")) {
    call()
  }

  override suspend fun disconnect(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME,
      MethodName("Disconnect")) {
    call()
  }

  override suspend fun connectProfile(uuid: String): Unit =
      proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("ConnectProfile")) {
    call(uuid)
  }

  override suspend fun disconnectProfile(uuid: String): Unit =
      proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("DisconnectProfile")) {
    call(uuid)
  }

  override suspend fun pair(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME,
      MethodName("Pair")) {
    call()
  }

  override suspend fun cancelPairing(): Unit =
      proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("CancelPairing")) {
    call()
  }
}
