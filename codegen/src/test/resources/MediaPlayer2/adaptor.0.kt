package org.mpris

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop

public abstract class MediaPlayer2Adaptor(
  public val obj: Object,
) : MediaPlayer2 {
  public override fun register() {
    obj.addVTable(MediaPlayer2.Companion.INTERFACE_NAME) {
      method(MethodName("Raise")) {
        acall(this@MediaPlayer2Adaptor::raise)
      }
      method(MethodName("Quit")) {
        acall(this@MediaPlayer2Adaptor::quit)
      }
      prop(PropertyName("CanQuit")) {
        with(this@MediaPlayer2Adaptor::canQuit)
      }
      prop(PropertyName("Fullscreen")) {
        with(this@MediaPlayer2Adaptor::fullscreen)
      }
      prop(PropertyName("CanSetFullscreen")) {
        with(this@MediaPlayer2Adaptor::canSetFullscreen)
      }
      prop(PropertyName("CanRaise")) {
        with(this@MediaPlayer2Adaptor::canRaise)
      }
      prop(PropertyName("HasTrackList")) {
        with(this@MediaPlayer2Adaptor::hasTrackList)
      }
      prop(PropertyName("Identity")) {
        with(this@MediaPlayer2Adaptor::identity)
      }
      prop(PropertyName("DesktopEntry")) {
        with(this@MediaPlayer2Adaptor::desktopEntry)
      }
      prop(PropertyName("SupportedUriSchemes")) {
        with(this@MediaPlayer2Adaptor::supportedUriSchemes)
      }
      prop(PropertyName("SupportedMimeTypes")) {
        with(this@MediaPlayer2Adaptor::supportedMimeTypes)
      }
    }
  }
}
