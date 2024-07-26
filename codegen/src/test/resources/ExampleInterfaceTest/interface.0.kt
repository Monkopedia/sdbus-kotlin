package com.example.MyService1

import kotlin.String
import kotlin.UInt

public interface InterestingInterface {
  public fun register()

  public suspend fun addContact(name: String, email: String): UInt

  public companion object {
    public const val INTERFACE_NAME: String = "com.example.MyService1.InterestingInterface"
  }
}
