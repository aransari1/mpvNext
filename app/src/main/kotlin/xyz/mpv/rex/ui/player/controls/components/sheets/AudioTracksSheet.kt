package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AudioChannels
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.TrackNode
import xyz.mpv.rex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject

@Composable
fun AudioTracksSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (TrackNode) -> Unit,
  onAddAudioTrack: () -> Unit,
  onOpenDelayPanel: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  alternateTracks: ImmutableList<xyz.mpv.rex.domain.ytdl.model.AudioTrack> = persistentListOf(),
  onSelectAlternate: (xyz.mpv.rex.domain.ytdl.model.AudioTrack) -> Unit = {},
) {
  val audioPreferences = koinInject<AudioPreferences>()
  val audioChannels by audioPreferences.audioChannels.collectAsState()

  GenericTracksSheet(
    tracks,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_audio),
        onAddAudioTrack,
        actions = {
          IconButton(onClick = onOpenDelayPanel) {
            Icon(Icons.Default.MoreTime, null)
          }
        },
      )
    },
    track = {
      AudioTrackCard(
        track = it,
        isSelected = it.isSelected,
        onClick = { onSelect(it) },
      )
    },
    footer = {
      Column {
        if (alternateTracks.isNotEmpty()) {
          Text(
            text = "Other audio languages",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
          for (track in alternateTracks) {
            AlternateAudioRow(
              track = track,
              onClick = { onSelectAlternate(track) },
            )
          }
        }
        androidx.compose.material3.Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
        ) {
          Text(
            text = stringResource(id = R.string.pref_audio_channels),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(8.dp))
          androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AudioChannels.entries.forEach {
              FilterChip(
                selected = audioChannels == it,
                onClick = {
                  audioPreferences.audioChannels.set(it)
                  if (it == AudioChannels.ReverseStereo) {
                    MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                  } else {
                    MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
                  }
                  MPVLib.setPropertyString(it.property, it.value)
                },
                label = { Text(text = stringResource(id = it.title)) },
                leadingIcon = null,
              )
            }
          }
        }
      }
      }
    },
    modifier = modifier,
  )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AudioTrackCard(
  track: TrackNode,
  isSelected: Boolean,
  onClick: () -> Unit,
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

  androidx.compose.material3.Surface(
    modifier = modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      RadioButton(
        selected = isSelected,
        onClick = onClick,
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
        val badges = remember(track) {
          buildList {
            track.lang?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            track.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            track.demuxChannels?.takeIf { it.isNotBlank() }?.let { add(it) }
              ?: track.audioChannels?.takeIf { it > 0 }?.let {
                add(when (it) {
                  1L -> "Mono"
                  2L -> "Stereo"
                  6L -> "5.1"
                  8L -> "7.1"
                  else -> "${it}ch"
                })
              }
            if (track.default == true) add("Default")
            if (track.forced == true) add("Forced")
            if (track.external == true) add("External")
            if (track.visualImpaired == true) add("Descriptive")
          }
        }
        if (badges.isNotEmpty()) {
          androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            for (badge in badges) {
              androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
              ) {
                Text(
                  text = badge,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
fun AudioTrackRow(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
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

  androidx.compose.material3.Surface(
    modifier = modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      RadioButton(
        selected = isSelected,
        onClick = onClick,
      )
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/**
 * A selectable alternate audio language that has NOT been attached to mpv yet.
 * Picking it attaches the stream on demand.
 */
@Composable
fun AlternateAudioRow(
  track: xyz.mpv.rex.domain.ytdl.model.AudioTrack,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  androidx.compose.material3.Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 2.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        Icons.Default.Add,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.label ?: track.lang ?: "Audio",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
