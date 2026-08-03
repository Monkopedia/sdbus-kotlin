package org.example.flags

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MutablePropertyDelegate
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.propDelegate
import com.monkopedia.sdbus.signalFlow
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlinx.coroutines.flow.Flow

public class LegacyProxy(
  public val proxy: Proxy,
) : Legacy {
  public val nameProperty: PropertyDelegate<LegacyProxy, String> =
      proxy.propDelegate(Legacy.Companion.INTERFACE_NAME, PropertyName("Name")) 

  public val oldNameProperty: PropertyDelegate<LegacyProxy, String> =
      proxy.propDelegate(Legacy.Companion.INTERFACE_NAME, PropertyName("OldName")) 

  public val machineIdProperty: PropertyDelegate<LegacyProxy, String> =
      proxy.propDelegate(Legacy.Companion.INTERFACE_NAME, PropertyName("MachineId")) 

  public var counterProperty: MutablePropertyDelegate<LegacyProxy, UInt> =
      proxy.mutableDelegate(Legacy.Companion.INTERFACE_NAME, PropertyName("Counter")) 

  public var silentProperty: MutablePropertyDelegate<LegacyProxy, Boolean> =
      proxy.mutableDelegate(Legacy.Companion.INTERFACE_NAME, PropertyName("Silent")) 

  override val name: String by nameProperty

  override val oldName: String by oldNameProperty

  override val machineId: String by machineIdProperty

  override var counter: UInt by counterProperty

  override var silent: Boolean by silentProperty

  public val changed: Flow<String> =
      proxy.signalFlow(Legacy.Companion.INTERFACE_NAME, SignalName("Changed")) {
        call { a: String -> a }
      }

  public val oldChanged: Flow<String> =
      proxy.signalFlow(Legacy.Companion.INTERFACE_NAME, SignalName("OldChanged")) {
        call { a: String -> a }
      }

  public override fun register() {
  }

  override suspend fun ping(message: String): String = proxy.callMethodAsync(Legacy.Companion.INTERFACE_NAME, MethodName("Ping")) {
    call(message)
  }

  override suspend fun oldPing(message: String): String = proxy.callMethodAsync(Legacy.Companion.INTERFACE_NAME, MethodName("OldPing")) {
    call(message)
  }

  override suspend fun fireAndForget(`value`: Int): Unit = proxy.callMethodAsync(Legacy.Companion.INTERFACE_NAME, MethodName("FireAndForget")) {
    dontExpectReply = true
    call(`value`)
  }
}
