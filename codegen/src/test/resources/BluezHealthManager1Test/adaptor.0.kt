package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class HealthManager1Adaptor(
  public val obj: Object,
) : HealthManager1 {
  public override fun register() {
    obj.addVTable(HealthManager1.Companion.INTERFACE_NAME) {
      method(MethodName("CreateApplication")) {
        inputParamNames = listOf("config")
        outputParamNames = listOf("application")
        acall(this@HealthManager1Adaptor::createApplication)
      }
      method(MethodName("DestroyApplication")) {
        inputParamNames = listOf("application")
        acall(this@HealthManager1Adaptor::destroyApplication)
      }
    }
  }
}
