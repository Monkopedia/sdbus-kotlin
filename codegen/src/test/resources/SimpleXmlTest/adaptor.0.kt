package org.foo

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.signal
import kotlin.Unit

public abstract class BackgroundAdaptor(
  public val obj: Object,
) : Background {
  public override fun register() {
    obj.addVTable(Background.Companion.INTERFACE_NAME) {
      method(MethodName("refreshBackground")) {
        acall(this@BackgroundAdaptor::refreshBackground)
      }
      method(MethodName("currentBackground")) {
        acall(this@BackgroundAdaptor::currentBackground)
      }
      method(MethodName("setBackground")) {
        inputParamNames = listOf("name")
        acall(this@BackgroundAdaptor::setBackground)
      }
      signal(SignalName("backgroundChanged")) {
      }
    }
  }

  public suspend fun onBackgroundChanged(): Unit =
      obj.emitSignal(Background.Companion.INTERFACE_NAME, SignalName("backgroundChanged")) {
    call()
  }
}
