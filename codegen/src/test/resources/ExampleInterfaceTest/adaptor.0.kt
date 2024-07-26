package com.example.MyService1

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class InterestingInterfaceAdaptor(
  protected val obj: IObject,
) : InterestingInterface {
  public override fun register() {
    obj.addVTable(InterestingInterface.Companion.INTERFACE_NAME) {
      method("AddContact") {
        inputParamNames = listOf("name", "email")
        outputParamNames = listOf("id")
        acall(this@InterestingInterfaceAdaptor::addContact)
      }
    }
  }
}
