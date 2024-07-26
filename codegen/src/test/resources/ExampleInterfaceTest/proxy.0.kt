package com.example.MyService1

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import kotlin.OptIn
import kotlin.String
import kotlin.UInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class InterestingInterfaceProxy(
  protected val proxy: IProxy,
) : InterestingInterface {
  public override fun register() {
    val weakRef = WeakReference(this)
  }

  override suspend fun addContact(name: String, email: String): UInt =
        proxy.callMethodAsync(InterestingInterface.Companion.INTERFACE_NAME, "AddContact") {
        call(name, email) }
}
