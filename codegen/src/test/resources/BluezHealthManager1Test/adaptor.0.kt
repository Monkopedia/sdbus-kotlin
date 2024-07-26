package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class HealthManager1Adaptor(
  protected val obj: IObject,
) : HealthManager1 {
  public override fun register() {
    obj.addVTable(HealthManager1.Companion.INTERFACE_NAME) {
      method("CreateApplication") {
        inputParamNames = listOf("config")
        outputParamNames = listOf("application")
        acall(this@HealthManager1Adaptor::createApplication)
      }
      method("DestroyApplication") {
        inputParamNames = listOf("application")
        outputParamNames = listOf()
        acall(this@HealthManager1Adaptor::destroyApplication)
      }
    }
  }
}
