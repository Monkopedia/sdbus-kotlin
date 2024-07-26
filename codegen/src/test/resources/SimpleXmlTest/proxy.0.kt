package org.foo

import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.onSignal
import kotlin.Boolean
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

@OptIn(ExperimentalNativeApi::class)
public abstract class BackgroundProxy(
  protected val proxy: IProxy,
) : Background {
  public override fun register() {
    val weakRef = WeakReference(this)
    proxy.onSignal(Background.Companion.INTERFACE_NAME, "backgroundChanged") {
      acall {
        ->
        weakRef.get()
          ?.onBackgroundChanged()
          ?: Unit
      }
    }
  }

  override suspend fun refreshBackground(): Unit =
        proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, "refreshBackground") { call() }

  override suspend fun currentBackground(): String =
        proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, "currentBackground") { call() }

  override suspend fun setBackground(name: String): Boolean =
        proxy.callMethodAsync(Background.Companion.INTERFACE_NAME, "setBackground") { call(name) }
}
