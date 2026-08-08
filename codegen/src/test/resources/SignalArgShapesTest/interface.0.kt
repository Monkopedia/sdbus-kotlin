package org.example.signals

import kotlin.String
import kotlin.UInt
import kotlinx.serialization.Serializable

@Serializable
public data class Entry(
  public val uInt: UInt,
  public val string: String,
)
