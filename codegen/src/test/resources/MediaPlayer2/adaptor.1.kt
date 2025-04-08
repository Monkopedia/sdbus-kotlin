package org.mpris.MediaPlayer2

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.Long
import kotlin.Unit

public abstract class PlayerAdaptor(
  public val obj: Object,
) : Player {
  public override fun register() {
    obj.addVTable(Player.Companion.INTERFACE_NAME) {
      method(MethodName("Next")) {
        acall(this@PlayerAdaptor::next)
      }
      method(MethodName("Previous")) {
        acall(this@PlayerAdaptor::previous)
      }
      method(MethodName("Pause")) {
        acall(this@PlayerAdaptor::pause)
      }
      method(MethodName("PlayPause")) {
        acall(this@PlayerAdaptor::playPause)
      }
      method(MethodName("Stop")) {
        acall(this@PlayerAdaptor::stop)
      }
      method(MethodName("Play")) {
        acall(this@PlayerAdaptor::play)
      }
      method(MethodName("Seek")) {
        inputParamNames = listOf("Offset")
        acall(this@PlayerAdaptor::seek)
      }
      method(MethodName("SetPosition")) {
        inputParamNames = listOf("TrackId", "Position")
        acall(this@PlayerAdaptor::setPosition)
      }
      method(MethodName("OpenUri")) {
        inputParamNames = listOf("Uri")
        acall(this@PlayerAdaptor::openUri)
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
