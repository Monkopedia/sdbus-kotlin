package com.example.myservice1

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class InterestingInterfaceAdaptor(
  public val obj: Object,
) : InterestingInterface {
  public override fun register() {
    obj.addVTable(InterestingInterface.Companion.INTERFACE_NAME) {
      method(MethodName("AddContact")) {
        inputParamNames = listOf("name", "email")
        outputParamNames = listOf("id")
        acall(this@InterestingInterfaceAdaptor::addContact)
      }
    }
  }
}
