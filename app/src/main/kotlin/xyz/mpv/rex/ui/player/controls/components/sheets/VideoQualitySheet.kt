package xyz.mpv.rex.ui.player.controls.components.sheets

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.ytdl.model.VideoQuality
import xyz.mpv.rex.preferences.YtdlPreferences
import xyz.mpv.rex.presentation.components.PlayerSheet

@Composable
fun VideoQualitySheet(
  qualities: List<VideoQuality>,
  currentQuality: VideoQuality?,
  onSelectQuality: (VideoQuality?) -> Unit,
  onDismissRequest: () -> Unit,
) {
  if (qualities.isEmpty()) {
    PlayerSheet(onDismissRequest) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = stringResource(R.string.video_quality),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.video_quality_addon_outdated),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }

  // Auto item representation: null means Auto / default resolved quality
  val isAutoSelected = currentQuality == null
  val context = LocalContext.current
  val ytdlPreferences = koinInject<YtdlPreferences>()

  PlayerSheet(onDismissRequest = onDismissRequest) {
    LazyColumn(
      state = rememberLazyListState(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      item {
        Text(
          text = stringResource(R.string.video_quality),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(bottom = 6.dp)
        )
      }

      // "Auto" Option card
      item {
        QualityCard(
          label = stringResource(R.string.video_quality_auto),
          subtitle = "Default / Extractor Choice",
          badge = "AUTO",
          isSelected = isAutoSelected,
          onClick = {
            onSelectQuality(null)
            onDismissRequest()
          },
        )
      }

      items(qualities, key = { it.id }) { quality ->
        val isSelected = currentQuality?.id == quality.id
        val subtitle = buildString {
          if (!quality.codec.isNullOrBlank()) append(quality.codec)
          if (quality.fps >= 50) {
            if (isNotEmpty()) append(" • ")
            append("${quality.fps}fps")
          }
          if (quality.bitrate > 0) {
            if (isNotEmpty()) append(" • ")
            val mbps = quality.bitrate / 1_000_000.0
            if (mbps >= 1.0) append(String.format("%.1f Mbps", mbps))
            else append("${quality.bitrate / 1000} Kbps")
          }
          if (quality.isAudioOnly) {
            append("Audio Stream")
          }
        }

        val badge = when {
          quality.isAudioOnly -> "AUDIO"
          quality.height >= 2160 -> "4K"
          quality.height >= 1440 -> "2K"
          quality.height >= 1080 -> "FHD"
          quality.height >= 720 -> "HD"
          quality.height > 0 -> "SD"
          else -> ""
        }

        QualityCard(
          label = quality.label,
          subtitle = subtitle.ifBlank { null },
          badge = badge,
          isSelected = isSelected,
          onClick = {
            onSelectQuality(quality)
            onDismissRequest()
          },
        )
      }

      // Button at the very bottom after all quality options to make current quality default
      item {
        val targetLabel = currentQuality?.shortLabel ?: stringResource(R.string.video_quality_auto)

        Button(
          onClick = {
            val prefValue = when {
              currentQuality == null -> "auto"
              currentQuality.isAudioOnly -> "audio_only"
              currentQuality.height > 0 -> currentQuality.height.toString()
              else -> "auto"
            }
            ytdlPreferences.qualityPreference.set(prefValue)
            Toast.makeText(
              context,
              context.getString(R.string.video_quality_default_saved, targetLabel),
              Toast.LENGTH_SHORT
            ).show()
            onDismissRequest()
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Text(
            text = stringResource(R.string.video_quality_set_as_default, targetLabel),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
fun QualityCard(
  label: String,
  subtitle: String?,
  badge: String,
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
  } else Color.Transparent

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
    shape = RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (isSelected) BorderStroke(1.dp, borderColor) else null,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RadioButton(
        selected = isSelected,
        onClick = onClick,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (badge.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
          else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
          Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    }
  }
}
