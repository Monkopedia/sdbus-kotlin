package com.example.doc

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Variant
import kotlin.String

/**
 * Documentation shapes that must not break the generated KDoc
 *
 * &#64;interface_name opens this paragraph, and being a paragraph it starts a line with no
 * preceding word to glue the reference to.
 */
public interface EdgeCases {
  /**
   * The mode currently in effect.
   *
   * &#35;org.example.doc.EdgeCases.Mode opens this second paragraph, which the doc DTD carrier
   * splits out on the blank line above.
   */
  public val mode: String

  public fun register()

  /**
   * &#35;org.example.Type opens the very first paragraph of this method
   *
   * A comment terminator &#42;/ in the middle of a sentence must not close the KDoc block early.
   *
   * @param pattern &#42;/ opens this argument description, so the tag body itself starts with a comment
   * terminator.
   * @return The value that matched.
   */
  public suspend fun match(pattern: String): Variant

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("com.example.doc.EdgeCases")
  }
}
