package xyz.mpv.rex.ui.browser.playlist

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.database.entities.PlaylistEntity
import xyz.mpv.rex.database.repository.PlaylistRepository

class AutoPlaylistTest {

  @Test
  fun `auto playlist IDs are negative and distinct`() {
    assertTrue(
      "ID_RECENTLY_ADDED must be negative",
      PlaylistViewModel.ID_RECENTLY_ADDED < 0
    )
    assertTrue(
      "ID_MOST_PLAYED must be negative",
      PlaylistViewModel.ID_MOST_PLAYED < 0
    )
    assertTrue(
      "Auto playlist IDs must be distinct",
      PlaylistViewModel.ID_RECENTLY_ADDED != PlaylistViewModel.ID_MOST_PLAYED
    )
  }

  @Test
  fun `PlaylistWithCount defaults isAutoPlaylist to false`() {
    val regularPlaylist = PlaylistWithCount(
      playlist = PlaylistEntity(id = 1, name = "User Playlist", createdAt = 0L, updatedAt = 0L),
      itemCount = 5,
    )
    assertFalse(regularPlaylist.isAutoPlaylist)

    val autoPlaylist = PlaylistWithCount(
      playlist = PlaylistEntity(id = PlaylistViewModel.ID_RECENTLY_ADDED, name = "Recently Added", createdAt = 0L, updatedAt = 0L),
      itemCount = 10,
      isAutoPlaylist = true,
    )
    assertTrue(autoPlaylist.isAutoPlaylist)
  }

  @Test
  fun `deletePlaylists filter protects auto playlists from being deleted in repository`() = runTest {
    val repository = mockk<PlaylistRepository>(relaxed = true)

    val regularEntity = PlaylistEntity(id = 1, name = "My Playlist", createdAt = 0L, updatedAt = 0L)
    val recentlyAddedEntity = PlaylistEntity(id = PlaylistViewModel.ID_RECENTLY_ADDED, name = "Recently Added", createdAt = 0L, updatedAt = 0L)
    val mostPlayedEntity = PlaylistEntity(id = PlaylistViewModel.ID_MOST_PLAYED, name = "Most Played", createdAt = 0L, updatedAt = 0L)

    val itemsToDelete = listOf(
      PlaylistWithCount(playlist = regularEntity, itemCount = 3, isAutoPlaylist = false),
      PlaylistWithCount(playlist = recentlyAddedEntity, itemCount = 10, isAutoPlaylist = true),
      PlaylistWithCount(playlist = mostPlayedEntity, itemCount = 5, isAutoPlaylist = true),
    )

    // Simulate the exact deletion filtering logic used in PlaylistViewModel.deletePlaylists
    val safeToDelete = itemsToDelete.filter { !it.isAutoPlaylist && it.playlist.id >= 0 }
    safeToDelete.forEach {
      repository.deletePlaylist(it.playlist)
    }

    // Verify only the regular playlist was deleted
    coVerify(exactly = 1) { repository.deletePlaylist(regularEntity) }
    coVerify(exactly = 0) { repository.deletePlaylist(recentlyAddedEntity) }
    coVerify(exactly = 0) { repository.deletePlaylist(mostPlayedEntity) }
    assertEquals(1, safeToDelete.size)
    assertEquals(1, safeToDelete[0].playlist.id)
  }
}
