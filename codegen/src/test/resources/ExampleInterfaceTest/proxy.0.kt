package com.example.myservice1

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

  /**
   * Adds a new contact to the address book with their name and e-mail address.
   *
   * @param name Name of new contact
   * @param email E-mail address of new contact
   * @return ID of newly added contact
   */
  override suspend fun addContact(name: String, email: String): UInt = proxy.callMethodAsync(InterestingInterface.Companion.INTERFACE_NAME, MethodName("AddContact")) {
    call(name, email)
  }
}
