package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.GroupedListItem
import xyz.mpv.rex.presentation.components.groupedItemShape

/**
 * Material 3 Expressive card container for grouping related preferences.
 * Supports standalone (GroupPosition.ONLY) or connected positions (FIRST, MIDDLE, LAST).
 */
@Composable
fun PreferenceCard(
  modifier: Modifier = Modifier,
  position: GroupPosition = GroupPosition.ONLY,
  color: Color? = null,
  tonalElevation: Dp = 1.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = groupedItemShape(position)
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .clip(shape),
    shape = shape,
    color = color ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = tonalElevation,
    shadowElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      content()
    }
  }
}

/**
 * Material 3 Expressive single-item preference card with connected shape geometry.
 */
@Composable
fun GroupedPreferenceCard(
  position: GroupPosition,
  modifier: Modifier = Modifier,
  highlightKey: Any? = null,
  color: Color? = null,
  tonalElevation: Dp = 1.dp,
  content: @Composable () -> Unit,
) {
  val shape = groupedItemShape(position)
  val highlightModifier = if (highlightKey != null) {
    Modifier.preferenceHighlight(highlightKey, shape)
  } else {
    Modifier
  }
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .then(highlightModifier)
      .clip(shape),
    shape = shape,
    color = color ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = tonalElevation,
    shadowElevation = 0.dp,
  ) {
    content()
  }
}

/**
 * Material 3 Expressive divider to separate preferences within a card.
 */
@Composable
fun PreferenceDivider(
  modifier: Modifier = Modifier,
) {
  HorizontalDivider(
    modifier = modifier.padding(horizontal = 20.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
  )
}

/**
 * Material 3 Expressive section header for preferences.
 */
@Composable
fun PreferenceSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(horizontal = 22.dp, vertical = 10.dp),
  )
}
