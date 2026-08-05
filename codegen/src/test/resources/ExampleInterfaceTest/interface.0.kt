package com.example.myservice1

import com.monkopedia.sdbus.InterfaceName
import kotlin.String
import kotlin.UInt

public interface InterestingInterface {
  public fun register()

  /**
   * Adds a new contact to the address book with their name and e-mail address.
   *
   * @param name Name of new contact
   * @param email E-mail address of new contact
   * @return ID of newly added contact
   */
  public suspend fun addContact(name: String, email: String): UInt

  public companion object {
    public val INTERFACE_NAME: InterfaceName =
        InterfaceName("com.example.MyService1.InterestingInterface")
  }
}
