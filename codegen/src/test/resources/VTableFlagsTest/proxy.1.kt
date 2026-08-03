package org.example.flags

import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.propDelegate
import kotlin.String

public class QuietProxy(
  public val proxy: Proxy,
) : Quiet {
  public val inheritedProperty: PropertyDelegate<QuietProxy, String> =
      proxy.propDelegate(Quiet.Companion.INTERFACE_NAME, PropertyName("Inherited")) 

  public val overriddenProperty: PropertyDelegate<QuietProxy, String> =
      proxy.propDelegate(Quiet.Companion.INTERFACE_NAME, PropertyName("Overridden")) 

  override val inherited: String by inheritedProperty

  override val overridden: String by overriddenProperty

  public override fun register() {
  }
}
