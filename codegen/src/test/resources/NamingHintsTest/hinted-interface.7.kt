package org.example

import kotlin.String
import kotlinx.serialization.Serializable

@Serializable
public data class QVersionSpec(
  public val string: String,
  public val string1: String,
  public val string2: String,
)
