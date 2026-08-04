package com.example.doc

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.propDelegate
import kotlin.String

/**
 * Documentation shapes that must not break the generated KDoc
 *
 * &#64;interface_name opens this paragraph, and being a paragraph it starts a line with no
 * preceding word to glue the reference to.
 */
public class EdgeCasesProxy(
  public val proxy: Proxy,
) : EdgeCases {
  public val modeProperty: PropertyDelegate<EdgeCasesProxy, String> =
      proxy.propDelegate(EdgeCases.Companion.INTERFACE_NAME, PropertyName("Mode")) 

  /**
   * The mode currently in effect.
   *
   * #org.example.doc.EdgeCases.Mode opens this second paragraph, which the doc DTD carrier splits
   * out on the blank line above.
   */
  override val mode: String by modeProperty

  public override fun register() {
  }

  /**
   * #org.example.Type opens the very first paragraph of this method
   *
   * A comment terminator &#42;/ in the middle of a sentence must not close the KDoc block early.
   *
   * @param pattern &#42;/ opens this argument description, so the tag body itself starts with a comment
   * terminator.
   * @return The value that matched.
   */
  override suspend fun match(pattern: String): Variant = proxy.callMethodAsync(EdgeCases.Companion.INTERFACE_NAME, MethodName("Match")) {
    call(pattern)
  }
}
