package org.example.flags

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.prop

public abstract class QuietAdaptor(
  public val obj: Object,
) : Quiet {
  public override fun register() {
    obj.addVTable(Quiet.Companion.INTERFACE_NAME) {
      prop(PropertyName("Inherited")) {
        with(this@QuietAdaptor::inherited)
        +EMITS_NO_SIGNAL
      }
      prop(PropertyName("Overridden")) {
        with(this@QuietAdaptor::overridden)
      }
    }
  }
}
