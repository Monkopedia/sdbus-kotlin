package org.bluez

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method

public abstract class ProfileManager1Adaptor(
  public val obj: Object,
) : ProfileManager1 {
  public override fun register() {
    obj.addVTable(ProfileManager1.Companion.INTERFACE_NAME) {
      method(MethodName("RegisterProfile")) {
        inputParamNames = listOf("profile", "UUID", "options")
        acall(this@ProfileManager1Adaptor::registerProfile)
      }
      method(MethodName("UnregisterProfile")) {
        inputParamNames = listOf("profile")
        acall(this@ProfileManager1Adaptor::unregisterProfile)
      }
    }
  }
}
