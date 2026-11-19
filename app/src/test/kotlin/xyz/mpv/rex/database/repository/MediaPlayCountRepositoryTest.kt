package xyz.mpv.rex.database.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.mpv.rex.database.dao.MediaPlayCountDao
import xyz.mpv.rex.database.entities.MediaPlayCountEntity

class MediaPlayCountRepositoryTest {
  private val dao = mockk<MediaPlayCountDao>(relaxed = true)
  private val repository = MediaPlayCountRepository(dao)

  @Test
  fun `incrementPlayCount calls dao with timestamp for valid path`() = runTest {
    val path = "/storage/emulated/0/Movies/video.mp4"
    val timestamp = 123456789L

    repository.incrementPlayCount(path, timestamp)

    coVerify(exactly = 1) { dao.incrementPlayCount(path, timestamp) }
  }

  @Test
  fun `incrementPlayCount ignores blank paths`() = runTest {
    repository.incrementPlayCount("")
    repository.incrementPlayCount("   ")

    coVerify(exactly = 0) { dao.incrementPlayCount(any(), any()) }
  }

  @Test
  fun `getMostPlayedEntities returns results from dao`() = runTest {
    val sampleEntities = listOf(
      MediaPlayCountEntity("/storage/A.mp4", playCount = 10, lastPlayedAt = 1000L),
      MediaPlayCountEntity("/storage/B.mp4", playCount = 5, lastPlayedAt = 2000L),
    )
    coEvery { dao.getMostPlayed(50) } returns sampleEntities

    val result = repository.getMostPlayedEntities(50)

    assertEquals(2, result.size)
    assertEquals(10, result[0].playCount)
    assertEquals("/storage/A.mp4", result[0].filePath)
  }

  @Test
  fun `observeMostPlayedEntities returns flow from dao`() = runTest {
    val sampleEntities = listOf(
      MediaPlayCountEntity("/storage/A.mp4", playCount = 3, lastPlayedAt = 1000L),
    )
    every { dao.observeMostPlayed(10) } returns flowOf(sampleEntities)

    val flow = repository.observeMostPlayedEntities(10)
    flow.collect { list ->
      assertEquals(1, list.size)
      assertEquals("/storage/A.mp4", list[0].filePath)
    }
  }

  @Test
  fun `getPlayCount returns play count or 0 if not found`() = runTest {
    coEvery { dao.getPlayCount("/storage/exists.mp4") } returns 7
    coEvery { dao.getPlayCount("/storage/unknown.mp4") } returns null

    assertEquals(7, repository.getPlayCount("/storage/exists.mp4"))
    assertEquals(0, repository.getPlayCount("/storage/unknown.mp4"))
  }

  @Test
  fun `updateFilePath delegates to dao`() = runTest {
    repository.updateFilePath("/old.mp4", "/new.mp4")

    coVerify(exactly = 1) { dao.updateFilePath("/old.mp4", "/new.mp4") }
  }

  @Test
  fun `deleteByFilePath delegates to dao`() = runTest {
    repository.deleteByFilePath("/deleted.mp4")

    coVerify(exactly = 1) { dao.deleteByFilePath("/deleted.mp4") }
  }

  @Test
  fun `clearAll delegates to dao`() = runTest {
    repository.clearAll()

    coVerify(exactly = 1) { dao.clearAll() }
  }
}
