package org.foo

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.signalFlow
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlinx.coroutines.flow.Flow

public class BackgroundProxy(
  public val proxy: Proxy,
) : Background {
  public val backgroundChanged: Flow<Unit> = proxy.signalFlow(Background.Companion.INTERFACE_NAME,
      SignalName("backgroundChanged")) {
        call { -> Unit }
      }

  public override fun register() {
  }

  override suspend fun refreshBackground(): Unit =
      proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, MethodName("refreshBackground")) {
    call()
  }

  override suspend fun currentBackground(): String =
      proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, MethodName("currentBackground")) {
    call()
  }

  override suspend fun setBackground(name: String): Boolean =
      proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, MethodName("setBackground")) {
    call(name)
  }
}
