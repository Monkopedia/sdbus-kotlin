package org.example.signals

import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.signal
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public abstract class ShapesAdaptor(
  public val obj: Object,
) : Shapes {
  public override fun register() {
    obj.addVTable(Shapes.Companion.INTERFACE_NAME) {
      signal(SignalName("NoArgs")) {
      }
      signal(SignalName("OneSimple")) {
        with<String>("reason")
      }
      signal(SignalName("OneArray")) {
        with<List<String>>("paths")
      }
      signal(SignalName("OneDict")) {
        with<Map<String, Variant>>("properties")
      }
      signal(SignalName("OneNestedDict")) {
        with<Map<ObjectPath, Map<String, Map<String, Variant>>>>("objects")
      }
      signal(SignalName("OneStruct")) {
        with<Entry>("entry")
      }
      signal(SignalName("TwoArgs")) {
        with<Int>("index")
        with<String>("label")
      }
    }
  }

  public suspend fun onNoArgs(): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("NoArgs")) {
    call()
  }

  public suspend fun onOneSimple(reason: String): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("OneSimple")) {
    call(reason)
  }

  public suspend fun onOneArray(paths: List<String>): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("OneArray")) {
    call(paths)
  }

  public suspend fun onOneDict(properties: Map<String, Variant>): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("OneDict")) {
    call(properties)
  }

  public suspend fun onOneNestedDict(objects: Map<ObjectPath, Map<String, Map<String, Variant>>>): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("OneNestedDict")) {
    call(objects)
  }

  public suspend fun onOneStruct(entry: Entry): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("OneStruct")) {
    call(entry)
  }

  public suspend fun onTwoArgs(index: Int, label: String): Unit = obj.emitSignal(Shapes.Companion.INTERFACE_NAME, SignalName("TwoArgs")) {
    call(index, label)
  }
}
