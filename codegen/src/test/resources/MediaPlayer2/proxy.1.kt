package org.mpris.mediaplayer2

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signalFlow
import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map
import kotlinx.coroutines.flow.Flow

public class PlayerProxy(
  public val proxy: Proxy,
) : Player {
  override val playbackStatus: String by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("PlaybackStatus")) 

  override var loopStatus: String by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("LoopStatus")) 

  override var rate: Double by proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("Rate")) 

  override var shuffle: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("Shuffle")) 

  override val metadata: Map<String, Variant> by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("Metadata")) 

  override var volume: Double by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("Volume")) 

  override val position: Long by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("Position")) 

  override val minimumRate: Double by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("MinimumRate")) 

  override val maximumRate: Double by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("MaximumRate")) 

  override val canGoNext: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("CanGoNext")) 

  override val canGoPrevious: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("CanGoPrevious")) 

  override val canPlay: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("CanPlay")) 

  override val canPause: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("CanPause")) 

  override val canSeek: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("CanSeek")) 

  override val canControl: Boolean by
      proxy.prop(Player.Companion.INTERFACE_NAME, PropertyName("CanControl")) 

  public val seeked: Flow<Long> =
      proxy.signalFlow(Player.Companion.INTERFACE_NAME, SignalName("Seeked")) {
        call { a: Long -> a }
      }

  public override fun register() {
  }

  override suspend fun next(): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("Next")) {
    call()
  }

  override suspend fun previous(): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("Previous")) {
    call()
  }

  override suspend fun pause(): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("Pause")) {
    call()
  }

  override suspend fun playPause(): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("PlayPause")) {
    call()
  }

  override suspend fun stop(): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("Stop")) {
    call()
  }

  override suspend fun play(): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("Play")) {
    call()
  }

  override suspend fun seek(offset: Long): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("Seek")) {
    call(offset)
  }

  override suspend fun setPosition(trackId: ObjectPath, position: Long): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("SetPosition")) {
    call(trackId, position)
  }

  override suspend fun openUri(uri: String): Unit = proxy.callMethodAsync(Player.Companion.INTERFACE_NAME, MethodName("OpenUri")) {
    call(uri)
  }
}
