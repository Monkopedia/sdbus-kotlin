package org.mpris.MediaPlayer2

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Variant
import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.String
import kotlin.collections.Map

public interface Player {
  public val playbackStatus: String

  public var loopStatus: String

  public var rate: Double

  public var shuffle: Boolean

  public val metadata: Map<String, Variant>

  public var volume: Double

  public val position: Long

  public val minimumRate: Double

  public val maximumRate: Double

  public val canGoNext: Boolean

  public val canGoPrevious: Boolean

  public val canPlay: Boolean

  public val canPause: Boolean

  public val canSeek: Boolean

  public val canControl: Boolean

  public fun register()

  public suspend fun next()

  public suspend fun previous()

  public suspend fun pause()

  public suspend fun playPause()

  public suspend fun stop()

  public suspend fun play()

  public suspend fun seek(offset: Long)

  public suspend fun setPosition(trackId: ObjectPath, position: Long)

  public suspend fun openUri(uri: String)

  public companion object {
    public val INTERFACE_NAME: InterfaceName = InterfaceName("org.mpris.MediaPlayer2.Player")
  }
}
