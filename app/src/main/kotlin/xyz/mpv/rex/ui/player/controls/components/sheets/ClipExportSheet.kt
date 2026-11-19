package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.FastClipper.ClipMode
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.theme.spacing
import java.util.Locale

@Composable
fun ClipExportSheet(
  startSec: Double,
  endSec: Double,
  onExport: (ClipMode) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val startFormatted = formatHighPrecisionTime(startSec)
  val endFormatted = formatHighPrecisionTime(endSec)
  val durationSec = String.format(Locale.US, "%.2fs", maxOf(0.0, endSec - startSec))

  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header (clean, no emoji/icon)
      Text(
        text = stringResource(R.string.clip_export_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
      )

      // Interval & Duration Summary Badge (borderless)
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "$startFormatted → $endFormatted",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false)
          )

          Spacer(modifier = Modifier.width(8.dp))

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = durationSec,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(2.dp))

      // Option 1: Fast (Lossless Copy) - borderless
      ExportOptionCard(
        icon = Icons.Default.Bolt,
        title = stringResource(R.string.clip_export_fast_title),
        tag = stringResource(R.string.clip_export_fast_tag),
        description = stringResource(R.string.clip_export_fast_desc),
        isPrimary = true,
        onClick = {
          onExport(ClipMode.FAST_COPY)
          onDismissRequest()
        }
      )

      // Option 2: Exact Frame (Transcode) - borderless
      ExportOptionCard(
        icon = Icons.Default.CenterFocusStrong,
        title = stringResource(R.string.clip_export_exact_title),
        tag = stringResource(R.string.clip_export_exact_tag),
        description = stringResource(R.string.clip_export_exact_desc),
        isPrimary = false,
        onClick = {
          onExport(ClipMode.FRAME_ACCURATE)
          onDismissRequest()
        }
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportOptionCard(
  icon: ImageVector,
  title: String,
  tag: String,
  description: String,
  isPrimary: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(
      containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      }
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .background(
            color = if (isPrimary) {
              MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            },
            shape = CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(22.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        FlowRow(
          verticalArrangement = Arrangement.spacedBy(4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isPrimary) {
              MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
            }
          ) {
            Text(
              text = tag,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun formatHighPrecisionTime(seconds: Double): String {
  val totalSec = seconds.toInt()
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  val ms = ((seconds - totalSec) * 1000).toInt().coerceIn(0, 999)
  return if (h > 0) {
    String.format(Locale.US, "%d:%02d:%02d.%03d", h, m, s, ms)
  } else {
    String.format(Locale.US, "%02d:%02d.%03d", m, s, ms)
  }
}
