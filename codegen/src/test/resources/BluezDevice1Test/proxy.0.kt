package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MutablePropertyDelegate
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.propDelegate
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
  public val addressProperty: PropertyDelegate<Device1Proxy, String> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Address")) 

  public val addressTypeProperty: PropertyDelegate<Device1Proxy, String> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("AddressType")) 

  public val nameProperty: PropertyDelegate<Device1Proxy, String> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Name")) 

  public val iconProperty: PropertyDelegate<Device1Proxy, String> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Icon")) 

  public val classProperty: PropertyDelegate<Device1Proxy, UInt> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Class")) 

  public val appearanceProperty: PropertyDelegate<Device1Proxy, UShort> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Appearance")) 

  public val uUIDsProperty: PropertyDelegate<Device1Proxy, List<String>> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("UUIDs")) 

  public val pairedProperty: PropertyDelegate<Device1Proxy, Boolean> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Paired")) 

  public val connectedProperty: PropertyDelegate<Device1Proxy, Boolean> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Connected")) 

  public var trustedProperty: MutablePropertyDelegate<Device1Proxy, Boolean> =
      proxy.mutableDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Trusted")) 

  public var blockedProperty: MutablePropertyDelegate<Device1Proxy, Boolean> =
      proxy.mutableDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Blocked")) 

  public var wakeAllowedProperty: MutablePropertyDelegate<Device1Proxy, Boolean> =
      proxy.mutableDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("WakeAllowed")) 

  public var aliasProperty: MutablePropertyDelegate<Device1Proxy, String> =
      proxy.mutableDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Alias")) 

  public val adapterProperty: PropertyDelegate<Device1Proxy, ObjectPath> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Adapter")) 

  public val legacyPairingProperty: PropertyDelegate<Device1Proxy, Boolean> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("LegacyPairing")) 

  public val modaliasProperty: PropertyDelegate<Device1Proxy, String> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("Modalias")) 

  public val rSSIProperty: PropertyDelegate<Device1Proxy, Short> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("RSSI")) 

  public val txPowerProperty: PropertyDelegate<Device1Proxy, Short> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("TxPower")) 

  public val manufacturerDataProperty: PropertyDelegate<Device1Proxy, Map<UShort, Variant>> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("ManufacturerData")) 

  public val serviceDataProperty: PropertyDelegate<Device1Proxy, Map<String, Variant>> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("ServiceData")) 

  public val servicesResolvedProperty: PropertyDelegate<Device1Proxy, Boolean> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("ServicesResolved")) 

  public val advertisingFlagsProperty: PropertyDelegate<Device1Proxy, List<Boolean>> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("AdvertisingFlags")) 

  public val advertisingDataProperty: PropertyDelegate<Device1Proxy, Map<UByte, Variant>> =
      proxy.propDelegate(Device1.Companion.INTERFACE_NAME, PropertyName("AdvertisingData")) 

  override val address: String by addressProperty

  override val addressType: String by addressTypeProperty

  override val name: String by nameProperty

  override val icon: String by iconProperty

  override val `class`: UInt by classProperty

  override val appearance: UShort by appearanceProperty

  override val uUIDs: List<String> by uUIDsProperty

  override val paired: Boolean by pairedProperty

  override val connected: Boolean by connectedProperty

  override var trusted: Boolean by trustedProperty

  override var blocked: Boolean by blockedProperty

  override var wakeAllowed: Boolean by wakeAllowedProperty

  override var alias: String by aliasProperty

  override val adapter: ObjectPath by adapterProperty

  override val legacyPairing: Boolean by legacyPairingProperty

  override val modalias: String by modaliasProperty

  override val rSSI: Short by rSSIProperty

  override val txPower: Short by txPowerProperty

  override val manufacturerData: Map<UShort, Variant> by manufacturerDataProperty

  override val serviceData: Map<String, Variant> by serviceDataProperty

  override val servicesResolved: Boolean by servicesResolvedProperty

  override val advertisingFlags: List<Boolean> by advertisingFlagsProperty

  override val advertisingData: Map<UByte, Variant> by advertisingDataProperty

  public override fun register() {
  }

  override suspend fun connect(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("Connect")) {
    call()
  }

  override suspend fun disconnect(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("Disconnect")) {
    call()
  }

  override suspend fun connectProfile(uuid: String): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("ConnectProfile")) {
    call(uuid)
  }

  override suspend fun disconnectProfile(uuid: String): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("DisconnectProfile")) {
    call(uuid)
  }

  override suspend fun pair(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("Pair")) {
    call()
  }

  override suspend fun cancelPairing(): Unit = proxy.callMethodAsync(Device1.Companion.INTERFACE_NAME, MethodName("CancelPairing")) {
    call()
  }
}
