package org.mpris

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MutablePropertyDelegate
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.propDelegate
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlin.collections.List

public class MediaPlayer2Proxy(
  public val proxy: Proxy,
) : MediaPlayer2 {
  public val canQuitProperty: PropertyDelegate<MediaPlayer2Proxy, Boolean> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("CanQuit")) 

  public var fullscreenProperty: MutablePropertyDelegate<MediaPlayer2Proxy, Boolean> =
      proxy.mutableDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("Fullscreen")) 

  public val canSetFullscreenProperty: PropertyDelegate<MediaPlayer2Proxy, Boolean> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("CanSetFullscreen")) 

  public val canRaiseProperty: PropertyDelegate<MediaPlayer2Proxy, Boolean> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("CanRaise")) 

  public val hasTrackListProperty: PropertyDelegate<MediaPlayer2Proxy, Boolean> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("HasTrackList")) 

  public val identityProperty: PropertyDelegate<MediaPlayer2Proxy, String> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("Identity")) 

  public val desktopEntryProperty: PropertyDelegate<MediaPlayer2Proxy, String> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("DesktopEntry")) 

  public val supportedUriSchemesProperty: PropertyDelegate<MediaPlayer2Proxy, List<String>> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("SupportedUriSchemes")) 

  public val supportedMimeTypesProperty: PropertyDelegate<MediaPlayer2Proxy, List<String>> =
      proxy.propDelegate(MediaPlayer2.Companion.INTERFACE_NAME, PropertyName("SupportedMimeTypes")) 

  override val canQuit: Boolean by canQuitProperty

  override var fullscreen: Boolean by fullscreenProperty

  override val canSetFullscreen: Boolean by canSetFullscreenProperty

  override val canRaise: Boolean by canRaiseProperty

  override val hasTrackList: Boolean by hasTrackListProperty

  override val identity: String by identityProperty

  override val desktopEntry: String by desktopEntryProperty

  override val supportedUriSchemes: List<String> by supportedUriSchemesProperty

  override val supportedMimeTypes: List<String> by supportedMimeTypesProperty

  public override fun register() {
  }

  override suspend fun raise(): Unit = proxy.callMethodAsync(MediaPlayer2.Companion.INTERFACE_NAME, MethodName("Raise")) {
    call()
  }

  override suspend fun quit(): Unit = proxy.callMethodAsync(MediaPlayer2.Companion.INTERFACE_NAME, MethodName("Quit")) {
    call()
  }
}
