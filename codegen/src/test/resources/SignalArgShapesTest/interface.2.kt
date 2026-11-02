package org.example.signals

import kotlin.Int
import kotlin.String
import kotlinx.serialization.Serializable

@Serializable
public data class TwoArgs(
  public val index: Int,
  public val label: String,
)
