package org.mpris

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.prop
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlin.collections.List

public class MediaPlayer2Proxy(
  public val proxy: Proxy,
) : MediaPlayer2 {
  override val canQuit: Boolean by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("CanQuit")) 

  override var fullscreen: Boolean by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("Fullscreen")) 

  override val canSetFullscreen: Boolean by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("CanSetFullscreen")) 

  override val canRaise: Boolean by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("CanRaise")) 

  override val hasTrackList: Boolean by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("HasTrackList")) 

  override val identity: String by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("Identity")) 

  override val desktopEntry: String by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("DesktopEntry")) 

  override val supportedUriSchemes: List<String> by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("SupportedUriSchemes")) 

  override val supportedMimeTypes: List<String> by
      proxy.prop(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("SupportedMimeTypes")) 

  public override fun register() {
  }

  override suspend fun raise(): Unit = proxy.callMethodAsync(MediaPlayer2.Companion.INTERFACE_NAME, MethodName("Raise")) {
    call()
  }

  override suspend fun quit(): Unit = proxy.callMethodAsync(MediaPlayer2.Companion.INTERFACE_NAME, MethodName("Quit")) {
    call()
  }
}
