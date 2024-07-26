package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class Adapter1Proxy(
  public val proxy: Proxy,
) : Adapter1 {
  override val address: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Address")) 

  override val addressType: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("AddressType")) 

  override val name: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, PropertyName("Name")) 

  override var alias: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, PropertyName("Alias"))
      

  override val `class`: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME, PropertyName("Class"))
      

  override var powered: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Powered")) 

  override var discoverable: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Discoverable")) 

  override var discoverableTimeout: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("DiscoverableTimeout")) 

  override var pairable: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Pairable")) 

  override var pairableTimeout: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("PairableTimeout")) 

  override val discovering: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Discovering")) 

  override val uUIDs: List<String> by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("UUIDs")) 

  override val modalias: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      PropertyName("Modalias")) 

  public override fun register() {
  }

  override suspend fun startDiscovery(): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("StartDiscovery")) {
    call()
  }

  override suspend fun setDiscoveryFilter(properties: Map<String, Variant>): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("SetDiscoveryFilter")) {
    call(properties)
  }

  override suspend fun stopDiscovery(): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("StopDiscovery")) {
    call()
  }

  override suspend fun removeDevice(device: ObjectPath): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("RemoveDevice")) {
    call(device)
  }

  override suspend fun getDiscoveryFilters(): List<String> =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("GetDiscoveryFilters")) {
    call()
  }

  override suspend fun connectDevice(properties: Map<String, Variant>): Unit =
      proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, MethodName("ConnectDevice")) {
    call(properties)
  }
}
