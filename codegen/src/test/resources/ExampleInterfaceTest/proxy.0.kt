package com.example.MyService1

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.UInt
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public class InterestingInterfaceProxy(
  public val proxy: IProxy,
) : InterestingInterface {
  public override fun register() {
  }

  override suspend fun addContact(name: String, email: String): UInt =
        proxy.callMethodAsync(InterestingInterface.Companion.INTERFACE_NAME, "AddContact") {
        call(name, email) }
}
