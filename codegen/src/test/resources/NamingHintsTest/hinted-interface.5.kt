package org.example

import kotlin.Short
import kotlinx.serialization.Serializable

@Serializable
public data class QSize(
  public val short: Short,
  public val short1: Short,
)
