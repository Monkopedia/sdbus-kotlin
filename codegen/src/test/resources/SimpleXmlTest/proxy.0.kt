package org.foo

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.signalFlow
import kotlin.Boolean
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalNativeApi::class)
public class BackgroundProxy(
  protected val proxy: IProxy,
) : Background {
  public val backgroundChanged: Flow<Unit> = proxy.signalFlow(Background.Companion.INTERFACE_NAME,
      "backgroundChanged") {
        call { -> Unit }
      }

  public override fun register() {
  }

  override suspend fun refreshBackground(): Unit =
        proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, "refreshBackground") { call() }

  override suspend fun currentBackground(): String =
        proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, "currentBackground") { call() }

  override suspend fun setBackground(name: String): Boolean =
        proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, "setBackground") { call(name) }
}
