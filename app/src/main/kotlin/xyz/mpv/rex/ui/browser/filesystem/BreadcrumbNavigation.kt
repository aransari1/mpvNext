package xyz.mpv.rex.ui.browser.filesystem

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.domain.browser.PathComponent
import xyz.mpv.rex.ui.theme.pillShape

@Composable
fun BreadcrumbNavigation(
  breadcrumbs: List<PathComponent>,
  onBreadcrumbClick: (PathComponent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val haptic = LocalHapticFeedback.current

  // Auto-scroll to end when breadcrumbs change with spring physics
  LaunchedEffect(breadcrumbs) {
    scrollState.animateScrollTo(
      scrollState.maxValue,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
      )
    )
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp),
    shape = pillShape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier
        .horizontalScroll(scrollState)
        .padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      breadcrumbs.forEachIndexed { index, component ->
        val isCurrent = index == breadcrumbs.lastIndex
        val isRoot = index == 0

        if (index > 0) {
          Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Separator",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
          )
        }

        val chipColor = if (isCurrent) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          Color.Transparent
        }
        val contentColor = if (isCurrent) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
          shape = pillShape,
          color = chipColor,
          contentColor = contentColor,
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBreadcrumbClick(component)
          },
          modifier = Modifier.animateContentSize(),
        ) {
          Row(
            modifier = Modifier.padding(
              horizontal = if (isRoot) 8.dp else 10.dp,
              vertical = 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            if (isRoot) {
              Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = "Root",
                modifier = Modifier.size(16.dp),
                tint = contentColor,
              )
            } else if (isCurrent) {
              Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = "Current Folder",
                modifier = Modifier.size(16.dp),
                tint = contentColor,
              )
            }

            Text(
              text = component.name,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
