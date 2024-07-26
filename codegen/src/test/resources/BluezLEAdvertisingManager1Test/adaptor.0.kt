package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class LEAdvertisingManager1Adaptor(
  public val obj: Object,
) : LEAdvertisingManager1 {
  public override fun register() {
    obj.addVTable(LEAdvertisingManager1.Companion.INTERFACE_NAME) {
      method(MethodName("RegisterAdvertisement")) {
        inputParamNames = listOf("advertisement", "options")
        acall(this@LEAdvertisingManager1Adaptor::registerAdvertisement)
      }
      method(MethodName("UnregisterAdvertisement")) {
        inputParamNames = listOf("service")
        acall(this@LEAdvertisingManager1Adaptor::unregisterAdvertisement)
      }
      prop(PropertyName("ActiveInstances")) {
        with(this@LEAdvertisingManager1Adaptor::activeInstances)
      }
      prop(PropertyName("SupportedInstances")) {
        with(this@LEAdvertisingManager1Adaptor::supportedInstances)
      }
      prop(PropertyName("SupportedIncludes")) {
        with(this@LEAdvertisingManager1Adaptor::supportedIncludes)
      }
    }
  }
}
