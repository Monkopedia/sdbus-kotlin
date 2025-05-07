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
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class Adapter1Proxy(
  public val proxy: Proxy,
) : Adapter1 {
  public val addressProperty: PropertyDelegate<Adapter1Proxy, String> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Address")) 

  public val addressTypeProperty: PropertyDelegate<Adapter1Proxy, String> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("AddressType")) 

  public val nameProperty: PropertyDelegate<Adapter1Proxy, String> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Name")) 

  public var aliasProperty: MutablePropertyDelegate<Adapter1Proxy, String> =
      proxy.mutableDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Alias")) 

  public val classProperty: PropertyDelegate<Adapter1Proxy, UInt> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Class")) 

  public var poweredProperty: MutablePropertyDelegate<Adapter1Proxy, Boolean> =
      proxy.mutableDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Powered")) 

  public var discoverableProperty: MutablePropertyDelegate<Adapter1Proxy, Boolean> =
      proxy.mutableDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Discoverable")) 

  public var discoverableTimeoutProperty: MutablePropertyDelegate<Adapter1Proxy, UInt> =
      proxy.mutableDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("DiscoverableTimeout")) 

  public var pairableProperty: MutablePropertyDelegate<Adapter1Proxy, Boolean> =
      proxy.mutableDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Pairable")) 

  public var pairableTimeoutProperty: MutablePropertyDelegate<Adapter1Proxy, UInt> =
      proxy.mutableDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("PairableTimeout")) 

  public val discoveringProperty: PropertyDelegate<Adapter1Proxy, Boolean> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Discovering")) 

  public val uUIDsProperty: PropertyDelegate<Adapter1Proxy, List<String>> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("UUIDs")) 

  public val modaliasProperty: PropertyDelegate<Adapter1Proxy, String> =
      proxy.propDelegate(Adapter1.Companion.INTERFACE_NAME, PropertyName("Modalias")) 

  override val address: String by addressProperty

  override val addressType: String by addressTypeProperty

  override val name: String by nameProperty

  override var alias: String by aliasProperty

  override val `class`: UInt by classProperty

  override var powered: Boolean by poweredProperty

  override var discoverable: Boolean by discoverableProperty

  override var discoverableTimeout: UInt by discoverableTimeoutProperty

  override var pairable: Boolean by pairableProperty

  override var pairableTimeout: UInt by pairableTimeoutProperty

  override val discovering: Boolean by discoveringProperty

  override val uUIDs: List<String> by uUIDsProperty

  override val modalias: String by modaliasProperty

  public override fun register() {
  }

  override suspend fun startDiscovery(): Unit = proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("StartDiscovery")) {
    call()
  }

  override suspend fun setDiscoveryFilter(properties: Map<String, Variant>): Unit = proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("SetDiscoveryFilter")) {
    call(properties)
  }

  override suspend fun stopDiscovery(): Unit = proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("StopDiscovery")) {
    call()
  }

  override suspend fun removeDevice(device: ObjectPath): Unit = proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("RemoveDevice")) {
    call(device)
  }

  override suspend fun getDiscoveryFilters(): List<String> = proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("GetDiscoveryFilters")) {
    call()
  }

  override suspend fun connectDevice(properties: Map<String, Variant>): Unit = proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("ConnectDevice")) {
    call(properties)
  }
}
