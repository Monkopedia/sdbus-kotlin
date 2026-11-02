package org.mpris

import com.monkopedia.sdbus.InterfaceName
import kotlin.Boolean
import kotlin.String
import kotlin.collections.List

public interface MediaPlayer2 {
  public val canQuit: Boolean

  public var fullscreen: Boolean

  public val canSetFullscreen: Boolean

  public val canRaise: Boolean

  public val hasTrackList: Boolean

  public val identity: String

  public val desktopEntry: String

  public val supportedUriSchemes: List<String>

  public val supportedMimeTypes: List<String>

  public fun register()

  public suspend fun raise()

  public suspend fun quit()

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.mpris.MediaPlayer2")
  }
}
