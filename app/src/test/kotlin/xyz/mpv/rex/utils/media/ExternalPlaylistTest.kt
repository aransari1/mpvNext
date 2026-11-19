package xyz.mpv.rex.utils.media

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.database.dao.NetworkConnectionDao
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.repository.NetworkRepository

class ExternalPlaylistTest {

  @Test
  fun `extractExternalPlaylist returns null when video_list is absent`() {
    val intent = mockk<Intent>(relaxed = true)
    every { intent.getParcelableArrayListExtra<Uri>(any(), any()) } returns null
    every { intent.getParcelableArrayListExtra<Parcelable>(any()) } returns null
    every { intent.getParcelableArrayExtra(any(), any<Class<Uri>>()) } returns null
    every { intent.getParcelableArrayExtra(any()) } returns null
    every { intent.getStringArrayExtra(any()) } returns null
    every { intent.getStringArrayListExtra(any()) } returns null

    val result = FolderPlaylistOps.extractExternalPlaylist(intent)
    assertNull(result)
  }

  @Test
  fun `extractExternalPlaylist extracts parcelable array of URIs and names`() {
    val uri1 = mockk<Uri>()
    val uri2 = mockk<Uri>()
    val uri3 = mockk<Uri>()
    every { uri1.lastPathSegment } returns "Ep01.mkv"
    every { uri2.lastPathSegment } returns "Ep02.mkv"
    every { uri3.lastPathSegment } returns "Ep03.mkv"
    every { uri1.path } returns "/Videos/Ep01.mkv"
    every { uri2.path } returns "/Videos/Ep02.mkv"
    every { uri3.path } returns "/Videos/Ep03.mkv"

    val intent = mockk<Intent>(relaxed = true)
    every { intent.getParcelableArrayListExtra<Uri>(any(), any()) } returns null
    every { intent.getParcelableArrayListExtra<Parcelable>(any()) } returns null
    every { intent.getParcelableArrayExtra("video_list") } returns arrayOf(uri1, uri2, uri3)
    every { intent.getParcelableArrayExtra("video_list", any<Class<Uri>>()) } returns arrayOf(uri1, uri2, uri3)
    every { intent.getParcelableArrayExtra("video_list", any<Class<Parcelable>>()) } returns arrayOf(uri1, uri2, uri3)
    every { intent.getStringArrayExtra("video_list") } returns null
    every { intent.getStringArrayListExtra("video_list") } returns null
    every { intent.getStringArrayExtra("video_list.name") } returns arrayOf("Episode 1", "Episode 2", "Episode 3")
    every { intent.getStringArrayListExtra("video_list.name") } returns null
    every { intent.getCharSequenceArrayExtra("video_list.name") } returns null
    every { intent.getCharSequenceArrayListExtra("video_list.name") } returns null
    every { intent.getBooleanExtra("video_list_is_explicit", false) } returns true
    every { intent.data } returns uri2

    val playlist = FolderPlaylistOps.extractExternalPlaylist(intent)
    assertNotNull(playlist)
    assertEquals(3, playlist!!.items.size)
    assertEquals(listOf(uri1, uri2, uri3), playlist.items)
    assertEquals(listOf("Episode 1", "Episode 2", "Episode 3"), playlist.titles)
    assertEquals(1, playlist.initialIndex)
    assertTrue(playlist.isExplicit)
  }

  @Test
  fun `extractExternalPlaylist falls back to lastPathSegment when names are absent`() {
    val uri1 = mockk<Uri>()
    val uri2 = mockk<Uri>()
    every { uri1.lastPathSegment } returns "video1.mp4"
    every { uri2.lastPathSegment } returns "video2.mp4"
    every { uri1.path } returns "/video1.mp4"
    every { uri2.path } returns "/video2.mp4"

    val intent = mockk<Intent>(relaxed = true)
    every { intent.getParcelableArrayListExtra<Uri>("video_list", any()) } returns arrayListOf(uri1, uri2)
    every { intent.getParcelableArrayListExtra<Parcelable>("video_list") } returns arrayListOf(uri1, uri2)
    every { intent.getStringArrayExtra("video_list.name") } returns null
    every { intent.getStringArrayListExtra("video_list.name") } returns null
    every { intent.getCharSequenceArrayExtra("video_list.name") } returns null
    every { intent.getCharSequenceArrayListExtra("video_list.name") } returns null
    every { intent.getBooleanExtra("video_list_is_explicit", false) } returns false
    every { intent.data } returns null

    val playlist = FolderPlaylistOps.extractExternalPlaylist(intent)
    assertNotNull(playlist)
    assertEquals(listOf("video1.mp4", "video2.mp4"), playlist!!.titles)
    assertEquals(0, playlist.initialIndex)
  }

  @Test
  fun `resolveNetworkConnection creates transient connection with embedded credentials`() = runBlocking {
    val smbPath = FolderPlaylistOps.parseSmbPath("smb://alice:secret123@192.168.1.50:4455/share/folder/file.mkv")
    assertNotNull(smbPath)

    val connection = FolderPlaylistOps.resolveNetworkConnection(smbPath!!)
    assertEquals("192.168.1.50", connection.host)
    assertEquals(4455, connection.port)
    assertEquals("alice", connection.username)
    assertEquals("secret123", connection.password)
    assertEquals("share", connection.path)
    assertEquals(false, connection.isAnonymous)
  }

  @Test
  fun `resolveNetworkConnection creates guest connection when no credentials or saved connection`() = runBlocking {
    val smbPath = FolderPlaylistOps.parseSmbPath("smb://192.168.1.99/public_share/video.mp4")
    assertNotNull(smbPath)

    val dao = mockk<NetworkConnectionDao>()
    val repository = NetworkRepository(dao)
    every { runBlocking { dao.getAllConnectionsList() } } returns emptyList()

    val connection = FolderPlaylistOps.resolveNetworkConnection(smbPath!!, repository)
    assertEquals("192.168.1.99", connection.host)
    assertEquals(445, connection.port)
    assertEquals(true, connection.isAnonymous)
    assertEquals("public_share", connection.path)
  }

  @Test
  fun `findSmbConnection matches case-insensitively and root connections to shares`() = runBlocking {
    val dao = mockk<NetworkConnectionDao>()
    val repository = NetworkRepository(dao)

    val rootConnection = NetworkConnection(
      id = 10,
      name = "NAS Root",
      protocol = NetworkProtocol.SMB,
      host = "MYNAS.LOCAL",
      port = 445,
      username = "user",
      password = "pwd",
      path = "/",
    )

    every { runBlocking { dao.getAllConnectionsList() } } returns listOf(rootConnection)

    val matched = repository.findSmbConnection("mynas.local", "movies")
    assertNotNull(matched)
    assertEquals(10L, matched!!.id)
    assertEquals("movies", matched.path)
  }

  @Test
  fun `findSmbConnection matches IPv6 bracketed hosts`() = runBlocking {
    val dao = mockk<NetworkConnectionDao>()
    val repository = NetworkRepository(dao)

    val ipv6Connection = NetworkConnection(
      id = 20,
      name = "IPv6 Server",
      protocol = NetworkProtocol.SMB,
      host = "[fe80::1]:445",
      port = 445,
      username = "user",
      password = "pwd",
      path = "shared",
    )

    every { runBlocking { dao.getAllConnectionsList() } } returns listOf(ipv6Connection)

    val matched = repository.findSmbConnection("fe80::1", "shared")
    assertNotNull(matched)
    assertEquals(20L, matched!!.id)
  }
}
