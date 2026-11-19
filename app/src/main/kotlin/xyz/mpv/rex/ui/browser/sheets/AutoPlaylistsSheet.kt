package xyz.mpv.rex.ui.browser.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.preferences.GroupedPreferenceCard
import xyz.mpv.rex.ui.preferences.components.SwitchPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistsSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  val browserPreferences = koinInject<BrowserPreferences>()
  val showRecentlyAdded by browserPreferences.showRecentlyAddedPlaylist.collectAsState()
  val showMostPlayed by browserPreferences.showMostPlayedPlaylist.collectAsState()

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 12.dp)
          .size(width = 32.dp, height = 4.dp)
          .background(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.extraLarge,
          )
      )
    },
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 32.dp),
    ) {
      // Header
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(bottom = 16.dp),
      ) {
        Text(
          text = stringResource(R.string.auto_playlists),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.auto_playlists_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
      }

      // Grouped M3E Preferences Card List
      GroupedListColumn {
        GroupedPreferenceCard(
          position = GroupPosition.FIRST,
        ) {
          SwitchPreference(
            value = showRecentlyAdded,
            onValueChange = { browserPreferences.showRecentlyAddedPlaylist.set(it) },
            title = { Text(text = stringResource(R.string.playlist_recently_added)) },
            summary = {
              Text(
                text = stringResource(R.string.playlist_recently_added_desc),
                color = MaterialTheme.colorScheme.outline,
              )
            },
          )
        }

        GroupedPreferenceCard(
          position = GroupPosition.LAST,
        ) {
          SwitchPreference(
            value = showMostPlayed,
            onValueChange = { browserPreferences.showMostPlayedPlaylist.set(it) },
            title = { Text(text = stringResource(R.string.playlist_most_played)) },
            summary = {
              Text(
                text = stringResource(R.string.playlist_most_played_desc),
                color = MaterialTheme.colorScheme.outline,
              )
            },
          )
        }
      }
    }
  }
}
