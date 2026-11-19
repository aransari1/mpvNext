package xyz.mpv.rex.ui.preferences.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Expressive single-choice pill group.
 *
 * Unlike [androidx.compose.material3.MultiChoiceSegmentedButtonRow] every segment is filled
 * rather than outlined: the selected one becomes a fully rounded `primary` pill with a check
 * mark, the rest stay rounded rectangles on `surfaceContainerHigh`. Colours cross-fade, so
 * moving the selection reads as one pill travelling through the row instead of two borders
 * swapping on and off.
 */
@Composable
fun ExpressiveSegmentedButton(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  buttonHeight: Dp = 48.dp,
  containerColor: Color = Color.Transparent,
  selectedColor: Color = MaterialTheme.colorScheme.primary,
  unselectedColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
  unselectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
  showCheckIcon: Boolean = true,
  enabled: Boolean = true,
) {
  Row(
    modifier = modifier
      .clip(CircleShape)
      .background(containerColor, CircleShape),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    options.forEachIndexed { index, label ->
      ExpressiveSegment(
        label = label,
        selected = index == selectedIndex,
        onClick = { onSelect(index) },
        height = buttonHeight,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        showCheckIcon = showCheckIcon,
        enabled = enabled,
      )
    }
  }
}

@Composable
private fun RowScope.ExpressiveSegment(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  height: Dp,
  selectedColor: Color,
  unselectedColor: Color,
  selectedContentColor: Color,
  unselectedContentColor: Color,
  showCheckIcon: Boolean,
  enabled: Boolean,
) {
  val container by animateColorAsState(
    targetValue = if (selected) selectedColor else unselectedColor,
    animationSpec = tween(durationMillis = 250),
    label = "segmentContainer",
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) selectedContentColor else unselectedContentColor,
    animationSpec = tween(durationMillis = 250),
    label = "segmentContent",
  )

  Box(
    modifier = Modifier
      .weight(1f)
      .height(height)
      .clip(if (selected) CircleShape else RoundedCornerShape(10.dp))
      .background(container)
      .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
      .semantics {
        this.selected = selected
      },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (showCheckIcon && selected) {
        Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          modifier = Modifier
            .padding(end = 6.dp)
            .size(18.dp),
          tint = contentColor,
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
      )
    }
  }
}

/** Center-aligned variant sized for a settings row's trailing control. */
@Composable
fun ExpressiveSegmentedButtonCompact(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  ExpressiveSegmentedButton(
    options = options,
    selectedIndex = selectedIndex,
    onSelect = onSelect,
    modifier = modifier.fillMaxWidth(),
    buttonHeight = 40.dp,
    showCheckIcon = false,
  )
}
