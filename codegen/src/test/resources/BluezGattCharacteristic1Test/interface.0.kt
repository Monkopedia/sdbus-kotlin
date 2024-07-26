package org.bluez

import com.monkopedia.sdbus.UnixFd
import kotlin.UShort
import kotlinx.serialization.Serializable

@Serializable
public data class AcquireType(
  public val fd: UnixFd,
  public val mtu: UShort,
)
