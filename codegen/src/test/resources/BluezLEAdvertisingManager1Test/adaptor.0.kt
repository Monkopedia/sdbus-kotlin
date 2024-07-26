package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class LEAdvertisingManager1Adaptor(
  protected val obj: IObject,
) : LEAdvertisingManager1 {
  public override fun register() {
    obj.addVTable(LEAdvertisingManager1.Companion.INTERFACE_NAME) {
      method("RegisterAdvertisement") {
        inputParamNames = listOf("advertisement", "options")
        outputParamNames = listOf()
        acall(this@LEAdvertisingManager1Adaptor::registerAdvertisement)
      }
      method("UnregisterAdvertisement") {
        inputParamNames = listOf("service")
        outputParamNames = listOf()
        acall(this@LEAdvertisingManager1Adaptor::unregisterAdvertisement)
      }
      prop("ActiveInstances") {
        with(this@LEAdvertisingManager1Adaptor::activeInstances)
      }
      prop("SupportedInstances") {
        with(this@LEAdvertisingManager1Adaptor::supportedInstances)
      }
      prop("SupportedIncludes") {
        with(this@LEAdvertisingManager1Adaptor::supportedIncludes)
      }
    }
  }
}
