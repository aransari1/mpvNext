package xyz.mpv.rex.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }

@Composable
fun groupedItemShape(position: GroupPosition): CornerBasedShape {
    val outerShape = MaterialTheme.shapes.extraLarge
    val innerShape = MaterialTheme.shapes.extraSmall
    return when (position) {
        GroupPosition.FIRST -> {
            outerShape.copy(
                bottomStart = innerShape.bottomStart,
                bottomEnd = innerShape.bottomEnd,
            )
        }

        GroupPosition.MIDDLE -> {
            innerShape
        }

        GroupPosition.LAST -> {
            outerShape.copy(
                topStart = innerShape.topStart,
                topEnd = innerShape.topEnd,
            )
        }

        GroupPosition.ONLY -> {
            outerShape
        }
    }
}

@Composable
fun GroupedListItem(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color? = null,
    tonalElevation: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    val shape = groupedItemShape(position)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .clip(shape),
            shape = shape,
            color = color ?: MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = tonalElevation,
            shadowElevation = 0.dp,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
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
}

@Composable
fun GroupedListColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        content()
    }
}
