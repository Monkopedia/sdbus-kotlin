package org.foo

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.signal
import kotlin.OptIn
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class BackgroundAdaptor(
  protected val obj: IObject,
) : Background {
  public override fun register() {
    obj.addVTable(Background.Companion.INTERFACE_NAME) {
      method("refreshBackground") {
        inputParamNames = listOf()
        outputParamNames = listOf()
        acall(this@BackgroundAdaptor::refreshBackground)
      }
      method("currentBackground") {
        inputParamNames = listOf()
        acall(this@BackgroundAdaptor::currentBackground)
      }
      method("setBackground") {
        inputParamNames = listOf("name")
        acall(this@BackgroundAdaptor::setBackground)
      }
      signal("backgroundChanged") {
      }
    }
  }

  override suspend fun onBackgroundChanged(): Unit = obj.emitSignal("backgroundChanged") { call() }
}
