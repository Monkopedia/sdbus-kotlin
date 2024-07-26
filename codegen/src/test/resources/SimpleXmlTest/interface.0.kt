package org.foo

import kotlin.Boolean
import kotlin.String

public interface Background {
  public fun register()

  public suspend fun refreshBackground()

  public suspend fun currentBackground(): String

  public suspend fun setBackground(name: String): Boolean

  public companion object {
    public const val INTERFACE_NAME: String = "org.foo.Background"
  }
}
