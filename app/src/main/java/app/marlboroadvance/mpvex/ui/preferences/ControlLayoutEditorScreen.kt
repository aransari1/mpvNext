package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.allPlayerButtons
import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.preferences.preference.deleteAndGet
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.ui.preferences.components.PlayerButtonChip
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.math.roundToInt

@Serializable
data class ControlLayoutEditorScreen(
  val region: ControlRegion,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()
    val portraitGridColumns by preferences.portraitGridColumns.collectAsState()

    val prefs = remember(region) {
      when (region) {
        ControlRegion.TOP_RIGHT -> listOf(preferences.topRightControls, preferences.topLeftControls, preferences.bottomRightControls, preferences.bottomLeftControls)
        ControlRegion.BOTTOM_RIGHT -> listOf(preferences.bottomRightControls, preferences.topLeftControls, preferences.topRightControls, preferences.bottomLeftControls)
        ControlRegion.BOTTOM_LEFT -> listOf(preferences.bottomLeftControls, preferences.topLeftControls, preferences.topRightControls, preferences.bottomRightControls)
        ControlRegion.PORTRAIT_BOTTOM -> listOf(preferences.portraitBottomControls)
        ControlRegion.MORE_SHEET -> listOf(preferences.moreSheetControls)
      }
    }

    val prefToEdit: Preference<String> = prefs[0]

        val usedInOtherRegions by remember(region) {
      mutableStateOf(
        if (region == ControlRegion.MORE_SHEET) {
          val landscapeSet = (preferences.topLeftControls.get().split(',') +
                  preferences.topRightControls.get().split(',') +
                  preferences.bottomLeftControls.get().split(',') +
                  preferences.bottomRightControls.get().split(','))
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .toSet()
          val portraitSet = preferences.portraitBottomControls.get().split(',')
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .toSet()
          landscapeSet.intersect(portraitSet)
        } else {
          val others = when (region) {
            ControlRegion.TOP_RIGHT -> listOf(preferences.topLeftControls, preferences.bottomRightControls, preferences.bottomLeftControls)
            ControlRegion.BOTTOM_RIGHT -> listOf(preferences.topLeftControls, preferences.topRightControls, preferences.bottomLeftControls)
            ControlRegion.BOTTOM_LEFT -> listOf(preferences.topLeftControls, preferences.topRightControls, preferences.bottomRightControls)
            ControlRegion.PORTRAIT_BOTTOM -> emptyList() // Portrait is independent
            else -> emptyList()
          }
          others.flatMap { it.get().split(',') }
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .toSet()
        }
      )
    }

    var selectedButtons by remember {
      mutableStateOf(
        if (region == ControlRegion.MORE_SHEET) {
          val landscapeSet = (preferences.topLeftControls.get().split(',') +
                  preferences.topRightControls.get().split(',') +
                  preferences.bottomLeftControls.get().split(',') +
                  preferences.bottomRightControls.get().split(','))
            .filter(String::isNotBlank)
            .toSet()
          val portraitSet = preferences.portraitBottomControls.get().split(',')
            .filter(String::isNotBlank)
            .toSet()
          val onBothScreens = landscapeSet.intersect(portraitSet)
          preferences.moreSheetControls.get().split(',')
            .filter(String::isNotBlank)
            .filter { it !in onBothScreens }
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
        } else {
          prefToEdit.get().split(',')
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
        }
      )
    }

    var showResetDialog by remember { mutableStateOf(false) }

    DisposableEffect(selectedButtons) {
      onDispose {
        prefToEdit.set(selectedButtons.joinToString(","))
      }
    }

    val title = when (region) {
      ControlRegion.TOP_RIGHT -> "Top-Right Region"
      ControlRegion.BOTTOM_RIGHT -> "Bottom-Right Region"
      ControlRegion.BOTTOM_LEFT -> "Bottom-Left Region"
      ControlRegion.PORTRAIT_BOTTOM -> "Portrait Controls"
      ControlRegion.MORE_SHEET -> "More Options Menu"
    }

    if (showResetDialog) {
      ConfirmDialog(
        title = "Reset Layout",
        subtitle = "Are you sure you want to reset this region to defaults?",
        onConfirm = {
          prefToEdit.deleteAndGet()
          backstack.removeLastOrNull()
        },
        onCancel = { showResetDialog = false },
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = title) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
          },
          actions = {
            IconButton(onClick = { showResetDialog = true }) {
              Icon(Icons.Outlined.Restore, contentDescription = "Reset to default")
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val gridState = rememberLazyGridState()
        val availableButtons = remember { allPlayerButtons.filter { it != PlayerButton.NONE } }
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
          val fromKey = from.key as? PlayerButton
          val toKey = to.key as? PlayerButton
          val fromIndex = selectedButtons.indexOf(fromKey)
          val toIndex = selectedButtons.indexOf(toKey)
          if (fromIndex in selectedButtons.indices && toIndex in selectedButtons.indices && fromIndex != toIndex) {
            selectedButtons = selectedButtons.toMutableList().apply {
              add(toIndex, removeAt(fromIndex))
            }
          }
        }

        LazyVerticalGrid(
          state = gridState,
          columns = GridCells.Adaptive(minSize = 72.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
          if (region == ControlRegion.PORTRAIT_BOTTOM) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  Text(
                    text = "Portrait Grid Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  SliderPreference(
                    value = portraitGridColumns.toFloat(),
                    onValueChange = { preferences.portraitGridColumns.set(it.roundToInt()) },
                    sliderValue = portraitGridColumns.toFloat(),
                    onSliderValueChange = { preferences.portraitGridColumns.set(it.roundToInt()) },
                    title = { Text(text = "Columns per row") },
                    summary = { Text(text = "$portraitGridColumns columns (Total capacity: ${portraitGridColumns * 2} buttons)") },
                    valueRange = 1f..10f,
                    valueSteps = 8
                  )
                }
              }
            }
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
              text = if (region == ControlRegion.PORTRAIT_BOTTOM) {
                "Buttons will be distributed to Top Row (first $portraitGridColumns) and Bottom Row (next $portraitGridColumns). Any extra will go to More Sheet."
              } else {
                "Long press to reorder items. Tap the '-' icon to remove them."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )
          }

          if (selectedButtons.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Column(
                  modifier = Modifier.fillMaxSize(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                  )
                  Text(
                    text = "Drop zone is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            selectedButtons.forEachIndexed { index, button ->
              if (region == ControlRegion.PORTRAIT_BOTTOM) {
                if (index == 0) {
                  item(span = { GridItemSpan(maxLineSpan) }, key = "marker_top") {
                    RowMarker(title = "TOP ROW", color = MaterialTheme.colorScheme.primary)
                  }
                } else if (index == portraitGridColumns) {
                  item(span = { GridItemSpan(maxLineSpan) }, key = "marker_bottom") {
                    RowMarker(title = "BOTTOM ROW", color = MaterialTheme.colorScheme.secondary)
                  }
                } else if (index == 2 * portraitGridColumns) {
                  item(span = { GridItemSpan(maxLineSpan) }, key = "marker_more") {
                    RowMarker(title = "MORE SHEET (OVERFLOW)", color = MaterialTheme.colorScheme.outline)
                  }
                }
              }

              item(
                key = button,
                span = {
                  if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) {
                    GridItemSpan(2)
                  } else {
                    GridItemSpan(1)
                  }
                }
              ) {
                ReorderableItem(reorderableState, key = button) { isDragging ->
                  val elevation by animateFloatAsState(targetValue = if (isDragging) 8f else 0f, label = "drag_elevation")
                  androidx.compose.material3.Surface(
                    modifier = Modifier.draggableHandle().then(
                      if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) Modifier.wrapContentWidth(Alignment.Start) else Modifier
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    shadowElevation = elevation.dp,
                    color = Color.Transparent
                  ) {
                    PlayerButtonChip(
                      button = button,
                      enabled = true,
                      onClick = { selectedButtons = selectedButtons - button },
                      badgeIcon = Icons.Default.RemoveCircle,
                      badgeColor = Color(0xFFEF5350),
                    )
                  }
                }
              }
            }
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(40.dp))
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
              text = "Available Palette",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
          }

          items(
            count = availableButtons.size,
            key = { "palette_${availableButtons[it].name}" },
            span = { index ->
              val button = availableButtons[index]
              if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) {
                GridItemSpan(2)
              } else {
                GridItemSpan(1)
              }
            }
          ) { index ->
            val button = availableButtons[index]
            val isUsed = button in selectedButtons || button in usedInOtherRegions
            PlayerButtonChip(
              button = button,
              enabled = !isUsed,
              onClick = { if (!isUsed) selectedButtons = selectedButtons + button },
              badgeIcon = Icons.Default.AddCircle,
              badgeColor = MaterialTheme.colorScheme.primary,
            )
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            IconsLegend()
            Spacer(Modifier.height(16.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun IconsLegend() {
  androidx.compose.material3.Card(
    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(text = "Icons Legend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
      FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        allPlayerButtons.forEach { button ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.wrapContentWidth()) {
            if (button == PlayerButton.AB_LOOP) {
              Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Text(text = "AB", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            } else {
              Icon(imageVector = button.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).then(if (button == PlayerButton.VERTICAL_FLIP) Modifier.rotate(90f) else Modifier))
            }
            Text(text = app.marlboroadvance.mpvex.preferences.getPlayerButtonLabel(button), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
          }
        }
      }
    }
  }
}

@Composable
private fun RowMarker(title: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
    androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = color.copy(alpha = 0.3f))
    Text(text = title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 12.dp))
    androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = color.copy(alpha = 0.3f))
  }
}
