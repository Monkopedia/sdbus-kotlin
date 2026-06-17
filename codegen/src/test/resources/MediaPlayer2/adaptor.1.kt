package org.mpris.mediaplayer2

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.notifying
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.String
import kotlin.Unit

public abstract class PlayerAdaptor(
  public val obj: Object,
) : Player {
  override var loopStatus: String by
      obj.notifying(Player.Companion.INTERFACE_NAME, PropertyName("LoopStatus"), "")

  override var rate: Double by
      obj.notifying(Player.Companion.INTERFACE_NAME, PropertyName("Rate"), 0.0)

  override var shuffle: Boolean by
      obj.notifying(Player.Companion.INTERFACE_NAME, PropertyName("Shuffle"), false)

  override var volume: Double by
      obj.notifying(Player.Companion.INTERFACE_NAME, PropertyName("Volume"), 0.0)

  public override fun register() {
    obj.addVTable(Player.Companion.INTERFACE_NAME) {
      method(MethodName("Next")) {
        asyncCall(this@PlayerAdaptor::next)
      }
      method(MethodName("Previous")) {
        asyncCall(this@PlayerAdaptor::previous)
      }
      method(MethodName("Pause")) {
        asyncCall(this@PlayerAdaptor::pause)
      }
      method(MethodName("PlayPause")) {
        asyncCall(this@PlayerAdaptor::playPause)
      }
      method(MethodName("Stop")) {
        asyncCall(this@PlayerAdaptor::stop)
      }
      method(MethodName("Play")) {
        asyncCall(this@PlayerAdaptor::play)
      }
      method(MethodName("Seek")) {
        inputParamNames = listOf("Offset")
        asyncCall(this@PlayerAdaptor::seek)
      }
      method(MethodName("SetPosition")) {
        inputParamNames = listOf("TrackId", "Position")
        asyncCall(this@PlayerAdaptor::setPosition)
      }
      method(MethodName("OpenUri")) {
        inputParamNames = listOf("Uri")
        asyncCall(this@PlayerAdaptor::openUri)
      }
      signal(SignalName("Seeked")) {
        with<Long>("Position")
      }
      prop(PropertyName("PlaybackStatus")) {
        with(this@PlayerAdaptor::playbackStatus)
      }
      prop(PropertyName("LoopStatus")) {
        with(this@PlayerAdaptor::loopStatus)
      }
      prop(PropertyName("Rate")) {
        with(this@PlayerAdaptor::rate)
      }
      prop(PropertyName("Shuffle")) {
        with(this@PlayerAdaptor::shuffle)
      }
      prop(PropertyName("Metadata")) {
        with(this@PlayerAdaptor::metadata)
      }
      prop(PropertyName("Volume")) {
        with(this@PlayerAdaptor::volume)
      }
      prop(PropertyName("Position")) {
        with(this@PlayerAdaptor::position)
      }
      prop(PropertyName("MinimumRate")) {
        with(this@PlayerAdaptor::minimumRate)
      }
      prop(PropertyName("MaximumRate")) {
        with(this@PlayerAdaptor::maximumRate)
      }
      prop(PropertyName("CanGoNext")) {
        with(this@PlayerAdaptor::canGoNext)
      }
      prop(PropertyName("CanGoPrevious")) {
        with(this@PlayerAdaptor::canGoPrevious)
      }
      prop(PropertyName("CanPlay")) {
        with(this@PlayerAdaptor::canPlay)
      }
      prop(PropertyName("CanPause")) {
        with(this@PlayerAdaptor::canPause)
      }
      prop(PropertyName("CanSeek")) {
        with(this@PlayerAdaptor::canSeek)
      }
      prop(PropertyName("CanControl")) {
        with(this@PlayerAdaptor::canControl)
      }
    }
  }

  public suspend fun onSeeked(position: Long): Unit = obj.emitSignal(Player.Companion.INTERFACE_NAME, SignalName("Seeked")) {
    call(position)
  }
}
