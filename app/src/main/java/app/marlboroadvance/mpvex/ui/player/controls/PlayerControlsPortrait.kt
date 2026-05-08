package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.theme.controlColor
import app.marlboroadvance.mpvex.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun TopPlayerControlsPortrait(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  viewModel: PlayerViewModel,
) {
  val appearancePreferences = org.koin.compose.koinInject<app.marlboroadvance.mpvex.preferences.AppearancePreferences>()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val playlistModeEnabled = viewModel.hasPlaylistSupport()
  val clickEvent = LocalPlayerButtonsClickEvent.current

  val surfaceColor = when {
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val contentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }

  val borderColor = if (hideBackground) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
  )

  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ControlsGroup {
        ControlsButton(
          icon = Icons.AutoMirrored.Default.ArrowBack,
          onClick = onBackPress,
        )

        val titleInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

        androidx.compose.foundation.layout.Box(
          modifier =
            Modifier
              .height(40.dp)
              .clip(CircleShape)
              .clickable(
                interactionSource = titleInteractionSource,
                indication = ripple(bounded = true),
                enabled = playlistModeEnabled,
                onClick = {
                  clickEvent()
                  onOpenSheet(Sheets.Playlist)
                },
              ),
        ) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = borderColor,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier
                  .padding(
                    horizontal = MaterialTheme.spacing.smaller,
                    vertical = MaterialTheme.spacing.smaller,
                  ),
            ) {
              Text(
                mediaTitle ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
              )
              viewModel.getPlaylistInfo()?.let { playlistInfo ->
                Text(
                  " • $playlistInfo",
                  maxLines = 1,
                  overflow = TextOverflow.Visible,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun PortraitControlsGrid(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: app.marlboroadvance.mpvex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  gridColumns: Int,
  modifier: Modifier = Modifier,
) {
  val spacing = MaterialTheme.spacing

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(spacing.smaller)
  ) {
    buttons.chunked(gridColumns).forEach { rowButtons ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        rowButtons.forEach { button ->
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
          ) {
            RenderPlayerButton(
              button = button,
              chapters = chapters,
              currentChapter = currentChapter,
              isPortrait = true,
              isSpeedNonOne = isSpeedNonOne,
              currentZoom = currentZoom,
              aspect = aspect,
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              onOpenPanel = onOpenPanel,
              viewModel = viewModel,
              activity = activity,
              decoder = decoder,
              playbackSpeed = playbackSpeed,
              buttonSize = 40.dp,
            )
          }
        }

        // Fill the remaining space in the last row to keep columns aligned
        val remaining = gridColumns - rowButtons.size
        if (remaining > 0) {
          repeat(remaining) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
fun TopPlayerControlsGridPortrait(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: app.marlboroadvance.mpvex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  gridColumns: Int,
) {
  PortraitControlsGrid(
    buttons = buttons,
    chapters = chapters,
    currentChapter = currentChapter,
    isSpeedNonOne = isSpeedNonOne,
    currentZoom = currentZoom,
    aspect = aspect,
    mediaTitle = mediaTitle,
    hideBackground = hideBackground,
    decoder = decoder,
    playbackSpeed = playbackSpeed,
    onBackPress = onBackPress,
    onOpenSheet = onOpenSheet,
    onOpenPanel = onOpenPanel,
    viewModel = viewModel,
    activity = activity,
    gridColumns = gridColumns,
    modifier = Modifier.padding(top = MaterialTheme.spacing.smaller)
  )
}

@Composable
fun BottomPlayerControlsPortrait(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: app.marlboroadvance.mpvex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  gridColumns: Int,
) {
  PortraitControlsGrid(
    buttons = buttons,
    chapters = chapters,
    currentChapter = currentChapter,
    isSpeedNonOne = isSpeedNonOne,
    currentZoom = currentZoom,
    aspect = aspect,
    mediaTitle = mediaTitle,
    hideBackground = hideBackground,
    decoder = decoder,
    playbackSpeed = playbackSpeed,
    onBackPress = onBackPress,
    onOpenSheet = onOpenSheet,
    onOpenPanel = onOpenPanel,
    viewModel = viewModel,
    activity = activity,
    gridColumns = gridColumns,
  )
}

