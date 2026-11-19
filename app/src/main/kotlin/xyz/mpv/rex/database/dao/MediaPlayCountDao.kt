package xyz.mpv.rex.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.database.entities.MediaPlayCountEntity

@Dao
interface MediaPlayCountDao {
  @Query("""
    INSERT INTO media_play_counts (filePath, playCount, lastPlayedAt)
    VALUES (:filePath, 1, :timestamp)
    ON CONFLICT(filePath) DO UPDATE SET
      playCount = playCount + 1,
      lastPlayedAt = :timestamp
  """)
  suspend fun incrementPlayCount(filePath: String, timestamp: Long)

  @Query("SELECT * FROM media_play_counts WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
  suspend fun getMostPlayed(limit: Int = 50): List<MediaPlayCountEntity>

  @Query("SELECT * FROM media_play_counts WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
  fun observeMostPlayed(limit: Int = 50): Flow<List<MediaPlayCountEntity>>

  @Query("SELECT playCount FROM media_play_counts WHERE filePath = :filePath LIMIT 1")
  suspend fun getPlayCount(filePath: String): Int?

  @Query("UPDATE media_play_counts SET filePath = :newPath WHERE filePath = :oldPath")
  suspend fun updateFilePath(oldPath: String, newPath: String)

  @Query("DELETE FROM media_play_counts WHERE filePath = :filePath")
  suspend fun deleteByFilePath(filePath: String)

  @Query("DELETE FROM media_play_counts")
  suspend fun clearAll()
}
