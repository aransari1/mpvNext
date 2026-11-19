package xyz.mpv.rex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks global playback frequency for media files to power dynamic auto-playlists
 * like "Most Played".
 */
@Entity(tableName = "media_play_counts")
data class MediaPlayCountEntity(
  @PrimaryKey val filePath: String,
  val playCount: Int = 1,
  val lastPlayedAt: Long,
)
