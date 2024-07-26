package org.bluez

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.String
import kotlin.UByte
import kotlin.collections.List
import kotlin.collections.Map

public interface GattDescriptor1 {
  public val uUID: String

  public val characteristic: ObjectPath

  public val `value`: List<UByte>

  public val flags: List<String>

  public fun register()

  public suspend fun readValue(options: Map<String, Variant>): List<UByte>

  public suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.bluez.GattDescriptor1")
  }
}
