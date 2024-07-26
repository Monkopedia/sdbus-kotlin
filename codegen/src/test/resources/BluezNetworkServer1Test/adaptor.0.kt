package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class NetworkServer1Adaptor(
  protected val obj: IObject,
) : NetworkServer1 {
  public override fun register() {
    obj.addVTable(NetworkServer1.Companion.INTERFACE_NAME) {
      method("Register") {
        inputParamNames = listOf("uuid", "bridge")
        outputParamNames = listOf()
        acall(this@NetworkServer1Adaptor::register)
      }
      method("Unregister") {
        inputParamNames = listOf("uuid")
        outputParamNames = listOf()
        acall(this@NetworkServer1Adaptor::unregister)
      }
    }
  }
}
