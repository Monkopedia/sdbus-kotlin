package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class Media1Adaptor(
  protected val obj: IObject,
) : Media1 {
  public override fun register() {
    obj.addVTable(Media1.Companion.INTERFACE_NAME) {
      method("RegisterEndpoint") {
        inputParamNames = listOf("endpoint", "properties")
        outputParamNames = listOf()
        acall(this@Media1Adaptor::registerEndpoint)
      }
      method("UnregisterEndpoint") {
        inputParamNames = listOf("endpoint")
        outputParamNames = listOf()
        acall(this@Media1Adaptor::unregisterEndpoint)
      }
      method("RegisterPlayer") {
        inputParamNames = listOf("player", "properties")
        outputParamNames = listOf()
        acall(this@Media1Adaptor::registerPlayer)
      }
      method("UnregisterPlayer") {
        inputParamNames = listOf("player")
        outputParamNames = listOf()
        acall(this@Media1Adaptor::unregisterPlayer)
      }
    }
  }
}
