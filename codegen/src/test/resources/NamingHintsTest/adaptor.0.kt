package org.example

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.Unit

public abstract class NamingAdaptor(
  public val obj: Object,
) : Naming {
  public override fun register() {
    obj.addVTable(Naming.Companion.INTERFACE_NAME) {
      method(MethodName("Transform")) {
        inputParamNames = listOf("origin")
        outputParamNames = listOf("path")
        asyncCall(this@NamingAdaptor::transform)
      }
      method(MethodName("Describe")) {
        inputParamNames = listOf("spec")
        outputParamNames = listOf("text")
        asyncCall(this@NamingAdaptor::describe)
      }
      method(MethodName("Bounds")) {
        outputParamNames = listOf("extent")
        asyncCall(this@NamingAdaptor::bounds)
      }
      signal(SignalName("Resized")) {
        with<Size>("size")
      }
      prop(PropertyName("Geometry")) {
        with(this@NamingAdaptor::geometry)
      }
      prop(PropertyName("Metadata")) {
        with(this@NamingAdaptor::metadata)
      }
    }
  }

  public suspend fun onResized(size: Size): Unit = obj.emitSignal(Naming.Companion.INTERFACE_NAME, SignalName("Resized")) {
    call(size)
  }
}
