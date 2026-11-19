package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.player.TrackNode
import xyz.mpv.rex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

sealed class SubtitleItem {
  data class Track(val node: TrackNode) : SubtitleItem()
  data class Header(val title: String) : SubtitleItem()
  object Divider : SubtitleItem()
}

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onToggleSubtitle: (Int) -> Unit,
  isSubtitleSelected: (Int) -> Boolean,
  onAddSubtitle: () -> Unit,
  onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  onRemoveSubtitle: (Int) -> Unit,
  onOpenOnlineSearch: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier
) {
  val primarySubtitleId = MPVLib.getPropertyInt("sid")

  val items = remember(tracks) {
    val list = mutableListOf<SubtitleItem>()
    
    // Internal/Local tracks section
    val internal = tracks.filter { it.external != true }
    val external = tracks.filter { it.external == true }
    
    if (internal.isNotEmpty() || external.isNotEmpty()) {
        list.add(SubtitleItem.Header(if (internal.isNotEmpty()) "Embedded Subtitles" else "Local Subtitles"))
        list.addAll(internal.map { SubtitleItem.Track(it) })
        if (internal.isNotEmpty() && external.isNotEmpty()) {
          list.add(SubtitleItem.Header("External Subtitles"))
        }
        list.addAll(external.map { SubtitleItem.Track(it) })
    }

    list.toImmutableList()
  }
  GenericTracksSheet(
    tracks = items,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_sub),
        onAddSubtitle,
        actions = {
          IconButton(onClick = onOpenOnlineSearch) {
            Icon(Icons.Default.Search, null)
          }
          IconButton(onClick = onOpenSubtitleSettings) {
            Icon(Icons.Default.Settings, null)
          }
          IconButton(onClick = onOpenSubtitleDelay) {
            Icon(Icons.Default.MoreTime, null)
          }
        },
      )
    },
    track = { item ->
      when (item) {
        is SubtitleItem.Track -> {
          val track = item.node
          SubtitleTrackRow(
            track = track,
            isSelected = isSubtitleSelected(track.id),
            isPrimary = track.id == primarySubtitleId,
            isExternal = track.external == true,
            onToggle = { onToggleSubtitle(track.id) },
            onRemove = { onRemoveSubtitle(track.id) },
          )
        }
        is SubtitleItem.Header -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        SubtitleItem.Divider -> {
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
      }
    },
    footer = {
      Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    },
    modifier = modifier,
  )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SubtitleTrackRow(
  track: TrackNode,
  isSelected: Boolean,
  isPrimary: Boolean = false,
  isExternal: Boolean,
  onToggle: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val title = getTrackTitle(track)
  val containerColor = if (isSelected) {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  }
  val borderColor = if (isSelected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
  } else androidx.compose.ui.graphics.Color.Transparent

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
    onClick = onToggle,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Checkbox(
        checked = isSelected,
        onCheckedChange = { onToggle() },
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        val badges = remember(track, isPrimary) {
          buildList {
            if (isPrimary) add("Primary")
            track.lang?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            track.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            if (track.default == true) add("Default")
            if (track.forced == true) add("Forced")
            if (track.hearingImpaired == true) add("SDH")
            if (isExternal) add("External")
          }
        }
        if (badges.isNotEmpty()) {
          androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            for (badge in badges) {
              Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = if (badge == "Primary") MaterialTheme.colorScheme.primary
                        else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
              ) {
                Text(
                  text = badge,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = if (badge == "Primary") MaterialTheme.colorScheme.onPrimary
                          else if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
        }
      }
      if (isExternal) {
        IconButton(onClick = onRemove) {
          Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
fun SubtitleTrackRow(
  title: String,
  isSelected: Boolean,
  isPrimary: Boolean = false,
  isExternal: Boolean,
  onToggle: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val containerColor = if (isSelected) {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  }
  val borderColor = if (isSelected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
  } else androidx.compose.ui.graphics.Color.Transparent

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
    onClick = onToggle,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Checkbox(
        checked = isSelected,
        onCheckedChange = { onToggle() },
      )
      Text(
        text = if (isPrimary) "$title [primary]" else title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      if (isExternal) {
        IconButton(onClick = onRemove) {
          Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}
