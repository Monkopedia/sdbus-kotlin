package org.bluez

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.OptIn
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class Adapter1Proxy(
  protected val proxy: IProxy,
) : Adapter1 {
  override val address: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Address") 

  override val addressType: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "AddressType") 

  override val name: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Name") 

  override var alias: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Alias") 

  override val `class`: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Class") 

  override var powered: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Powered") 

  override var discoverable: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      "Discoverable") 

  override var discoverableTimeout: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      "DiscoverableTimeout") 

  override var pairable: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Pairable") 

  override var pairableTimeout: UInt by proxy.prop(Adapter1.Companion.INTERFACE_NAME,
      "PairableTimeout") 

  override val discovering: Boolean by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Discovering") 

  override val uUIDs: List<String> by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "UUIDs") 

  override val modalias: String by proxy.prop(Adapter1.Companion.INTERFACE_NAME, "Modalias") 

  public override fun register() {
  }

  override suspend fun startDiscovery(): Unit =
        proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, "StartDiscovery") { call() }

  override suspend fun setDiscoveryFilter(properties: Map<String, Variant>): Unit =
        proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, "SetDiscoveryFilter") {
        call(properties) }

  override suspend fun stopDiscovery(): Unit =
        proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, "StopDiscovery") { call() }

  override suspend fun removeDevice(device: ObjectPath): Unit =
        proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, "RemoveDevice") { call(device) }

  override suspend fun getDiscoveryFilters(): List<String> =
        proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, "GetDiscoveryFilters") { call() }

  override suspend fun connectDevice(properties: Map<String, Variant>): Unit =
        proxy.callMethodAsync(Adapter1.Companion.INTERFACE_NAME, "ConnectDevice") { call(properties)
        }
}
