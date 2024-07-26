package org.bluez

import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import kotlin.Boolean
import kotlin.String
import kotlin.UByte
import kotlin.collections.List
import kotlin.collections.Map

public interface GattCharacteristic1 {
  public val uUID: String

  public val service: ObjectPath

  public val `value`: List<UByte>

  public val directedValue: Map<UnixFd, List<UByte>>

  public val notifying: Boolean

  public val flags: List<String>

  public val descriptors: List<ObjectPath>

  public val writeAcquired: Boolean

  public val notifyAcquired: Boolean

  public fun register()

  public suspend fun readValue(options: Map<String, Variant>): List<UByte>

  public suspend fun writeValue(`value`: List<UByte>, options: Map<String, Variant>)

  public suspend fun acquireWrite(options: Map<String, Variant>): AcquireType

  public suspend fun acquireNotify(options: Map<String, Variant>): AcquireType

  public suspend fun startNotify()

  public suspend fun stopNotify()

  public suspend fun confirm()

  public companion object {
    public const val INTERFACE_NAME: String = "org.bluez.GattCharacteristic1"
  }
}
