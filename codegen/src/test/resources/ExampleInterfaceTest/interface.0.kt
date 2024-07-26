package com.example.MyService1

import com.monkopedia.sdbus.InterfaceName
import kotlin.String
import kotlin.UInt

public interface InterestingInterface {
  public fun register()

  public suspend fun addContact(name: String, email: String): UInt

  public companion object {
    public val INTERFACE_NAME: InterfaceName =
        InterfaceName("com.example.MyService1.InterestingInterface")
  }
}
