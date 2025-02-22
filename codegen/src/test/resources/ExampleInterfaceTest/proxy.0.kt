package com.example.MyService1

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import kotlin.String
import kotlin.UInt

public class InterestingInterfaceProxy(
  public val proxy: Proxy,
) : InterestingInterface {
  public override fun register() {
  }

  override suspend fun addContact(name: String, email: String): UInt = proxy.callMethodAsync(InterestingInterface.Companion.INTERFACE_NAME, MethodName("AddContact")) {
    call(name, email)
  }
}
