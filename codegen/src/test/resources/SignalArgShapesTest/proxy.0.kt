package org.example.signals

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.signalFlow
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlinx.coroutines.flow.Flow

public class ShapesProxy(
  public val proxy: Proxy,
) : Shapes {
  public val noArgs: Flow<Unit> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("NoArgs")) {
        call { -> Unit }
      }

  public val oneSimple: Flow<String> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("OneSimple")) {
        call { a: String -> a }
      }

  public val oneArray: Flow<List<String>> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("OneArray")) {
        call { a: List<String> -> a }
      }

  public val oneDict: Flow<Map<String, Variant>> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("OneDict")) {
        call { a: Map<String, Variant> -> a }
      }

  public val oneNestedDict: Flow<Map<ObjectPath, Map<String, Map<String, Variant>>>> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("OneNestedDict")) {
        call { a: Map<ObjectPath, Map<String, Map<String, Variant>>> -> a }
      }

  public val oneStruct: Flow<Entry> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("OneStruct")) {
        call { a: Entry -> a }
      }

  public val twoArgs: Flow<TwoArgs> =
      proxy.signalFlow(Shapes.Companion.INTERFACE_NAME, SignalName("TwoArgs")) {
        call(::TwoArgs)
      }

  public override fun register() {
  }
}
