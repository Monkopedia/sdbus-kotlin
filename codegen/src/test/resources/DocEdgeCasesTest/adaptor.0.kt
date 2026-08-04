package com.example.doc

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

/**
 * Documentation shapes that must not break the generated KDoc
 *
 * &#64;interface_name opens this paragraph, and being a paragraph it starts a line with no
 * preceding word to glue the reference to.
 */
public abstract class EdgeCasesAdaptor(
  public val obj: Object,
) : EdgeCases {
  public override fun register() {
    obj.addVTable(EdgeCases.Companion.INTERFACE_NAME) {
      method(MethodName("Match")) {
        inputParamNames = listOf("pattern")
        outputParamNames = listOf("value")
        asyncCall(this@EdgeCasesAdaptor::match)
      }
      prop(PropertyName("Mode")) {
        with(this@EdgeCasesAdaptor::mode)
      }
    }
  }
}
