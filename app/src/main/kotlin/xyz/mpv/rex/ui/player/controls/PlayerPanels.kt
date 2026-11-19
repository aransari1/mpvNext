package xyz.mpv.rex.ui.player.controls

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.controls.components.panels.AudioDelayPanel
import xyz.mpv.rex.ui.player.controls.components.panels.SubtitleDelayPanel
import xyz.mpv.rex.ui.player.controls.components.panels.SubtitleSettingsPanel
import xyz.mpv.rex.ui.player.controls.components.panels.VideoSettingsPanel

@Composable
fun PlayerPanels(
  panelShown: Panels,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (panelShown) {
    Panels.None -> {}
    Panels.SubtitleSettings -> {
      SubtitleSettingsPanel(onDismissRequest, modifier)
    }
    Panels.SubtitleDelay -> {
      SubtitleDelayPanel(onDismissRequest, modifier)
    }
    Panels.AudioDelay -> {
      AudioDelayPanel(onDismissRequest, modifier)
    }
    Panels.VideoFilters -> {
      VideoSettingsPanel(onDismissRequest, modifier)
    }
  }
}

val CARDS_MAX_WIDTH = 420.dp
val panelCardsColors: @Composable () -> CardColors = {
  // Higher alpha for better readability in panels (less transparent)
  val alpha = 0.85f

  CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha),
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha),
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
  )
}
