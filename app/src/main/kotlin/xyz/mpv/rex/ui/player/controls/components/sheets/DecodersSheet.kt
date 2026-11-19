package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.player.Decoder
import kotlinx.collections.immutable.toImmutableList

@Composable
fun DecodersSheet(
  selectedDecoder: Decoder,
  onSelect: (Decoder) -> Unit,
  onDismissRequest: () -> Unit,
) {
  GenericTracksSheet(
    Decoder.entries.minusElement(Decoder.Auto).toImmutableList(),
    track = {
      DecoderCard(
        decoder = it,
        isSelected = selectedDecoder == it,
        onClick = { onSelect(it) },
      )
    },
    onDismissRequest = onDismissRequest,
  )
}

@Composable
fun DecoderCard(
  decoder: Decoder,
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
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (isSelected) BorderStroke(1.dp, borderColor) else null,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RadioButton(
        selected = isSelected,
        onClick = onClick,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = decoder.title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = decoder.value,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
      ) {
        Text(
          text = when (decoder) {
            Decoder.HWPlus -> "HW+"
            Decoder.HW -> "HW"
            Decoder.SW -> "SW"
            else -> decoder.name
          },
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
