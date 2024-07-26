package org.bluez

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class ProfileManager1Adaptor(
  protected val obj: IObject,
) : ProfileManager1 {
  public override fun register() {
    obj.addVTable(ProfileManager1.Companion.INTERFACE_NAME) {
      method("RegisterProfile") {
        inputParamNames = listOf("profile", "UUID", "options")
        outputParamNames = listOf()
        acall(this@ProfileManager1Adaptor::registerProfile)
      }
      method("UnregisterProfile") {
        inputParamNames = listOf("profile")
        outputParamNames = listOf()
        acall(this@ProfileManager1Adaptor::unregisterProfile)
      }
    }
  }
}
