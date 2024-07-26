package org.freedesktop.DBus

import com.monkopedia.sdbus.IObject
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import kotlin.OptIn
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public abstract class ObjectManagerAdaptor(
  protected val obj: IObject,
) : ObjectManager {
  public override fun register() {
    obj.addVTable(ObjectManager.Companion.INTERFACE_NAME) {
      method("GetManagedObjects") {
        inputParamNames = listOf()
        outputParamNames = listOf("value")
        acall(this@ObjectManagerAdaptor::getManagedObjects)
      }
    }
  }
}
