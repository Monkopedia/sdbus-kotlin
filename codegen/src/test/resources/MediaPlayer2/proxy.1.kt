package org.mpris.mediaplayer2

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MutablePropertyDelegate
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.propDelegate
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
  public val playbackStatusProperty: PropertyDelegate<PlayerProxy, String> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("PlaybackStatus")) 

  public var loopStatusProperty: MutablePropertyDelegate<PlayerProxy, String> =
      proxy.mutableDelegate(Player.Companion.INTERFACE_NAME, PropertyName("LoopStatus")) 

  public var rateProperty: MutablePropertyDelegate<PlayerProxy, Double> =
      proxy.mutableDelegate(Player.Companion.INTERFACE_NAME, PropertyName("Rate")) 

  public var shuffleProperty: MutablePropertyDelegate<PlayerProxy, Boolean> =
      proxy.mutableDelegate(Player.Companion.INTERFACE_NAME, PropertyName("Shuffle")) 

  public val metadataProperty: PropertyDelegate<PlayerProxy, Map<String, Variant>> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("Metadata")) 

  public var volumeProperty: MutablePropertyDelegate<PlayerProxy, Double> =
      proxy.mutableDelegate(Player.Companion.INTERFACE_NAME, PropertyName("Volume")) 

  public val positionProperty: PropertyDelegate<PlayerProxy, Long> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("Position")) 

  public val minimumRateProperty: PropertyDelegate<PlayerProxy, Double> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("MinimumRate")) 

  public val maximumRateProperty: PropertyDelegate<PlayerProxy, Double> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("MaximumRate")) 

  public val canGoNextProperty: PropertyDelegate<PlayerProxy, Boolean> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("CanGoNext")) 

  public val canGoPreviousProperty: PropertyDelegate<PlayerProxy, Boolean> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("CanGoPrevious")) 

  public val canPlayProperty: PropertyDelegate<PlayerProxy, Boolean> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("CanPlay")) 

  public val canPauseProperty: PropertyDelegate<PlayerProxy, Boolean> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("CanPause")) 

  public val canSeekProperty: PropertyDelegate<PlayerProxy, Boolean> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("CanSeek")) 

  public val canControlProperty: PropertyDelegate<PlayerProxy, Boolean> =
      proxy.propDelegate(Player.Companion.INTERFACE_NAME, PropertyName("CanControl")) 

  override val playbackStatus: String by playbackStatusProperty

  override var loopStatus: String by loopStatusProperty

  override var rate: Double by rateProperty

  override var shuffle: Boolean by shuffleProperty

  override val metadata: Map<String, Variant> by metadataProperty

  override var volume: Double by volumeProperty

  override val position: Long by positionProperty

  override val minimumRate: Double by minimumRateProperty

  override val maximumRate: Double by maximumRateProperty

  override val canGoNext: Boolean by canGoNextProperty

  override val canGoPrevious: Boolean by canGoPreviousProperty

  override val canPlay: Boolean by canPlayProperty

  override val canPause: Boolean by canPauseProperty

  override val canSeek: Boolean by canSeekProperty

  override val canControl: Boolean by canControlProperty

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
