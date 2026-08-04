package org.example.flags

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.interfaceFlags
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.notifying
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.String
import kotlin.UInt
import kotlin.Unit

public abstract class LegacyAdaptor(
  public val obj: Object,
) : Legacy {
  override var counter: UInt by
      obj.notifying(Legacy.Companion.INTERFACE_NAME, PropertyName("Counter"), 0u)

  public override fun register() {
    obj.addVTable(Legacy.Companion.INTERFACE_NAME) {
      interfaceFlags {
        isDeprecated = true
      }
      method(MethodName("Ping")) {
        inputParamNames = listOf("message")
        outputParamNames = listOf("reply")
        asyncCall(this@LegacyAdaptor::ping)
      }
      method(MethodName("OldPing")) {
        inputParamNames = listOf("message")
        outputParamNames = listOf("reply")
        isDeprecated = true
        asyncCall(this@LegacyAdaptor::oldPing)
      }
      method(MethodName("FireAndForget")) {
        inputParamNames = listOf("value")
        hasNoReply = true
        asyncCall(this@LegacyAdaptor::fireAndForget)
      }
      signal(SignalName("Changed")) {
        with<String>("reason")
      }
      signal(SignalName("OldChanged")) {
        with<String>("reason")
        isDeprecated = true
      }
      prop(PropertyName("Name")) {
        with(this@LegacyAdaptor::name)
      }
      prop(PropertyName("OldName")) {
        with(this@LegacyAdaptor::oldName)
        isDeprecated = true
      }
      prop(PropertyName("MachineId")) {
        with(this@LegacyAdaptor::machineId)
        +CONST_PROPERTY_VALUE
      }
      prop(PropertyName("Counter")) {
        with(this@LegacyAdaptor::counter)
        +EMITS_INVALIDATION_SIGNAL
      }
      prop(PropertyName("Silent")) {
        with(this@LegacyAdaptor::silent)
        +EMITS_NO_SIGNAL
      }
    }
  }

  public suspend fun onChanged(reason: String): Unit = obj.emitSignal(Legacy.Companion.INTERFACE_NAME, SignalName("Changed")) {
    call(reason)
  }

  public suspend fun onOldChanged(reason: String): Unit = obj.emitSignal(Legacy.Companion.INTERFACE_NAME, SignalName("OldChanged")) {
    call(reason)
  }
}
