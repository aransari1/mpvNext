package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

@Composable
fun VideoZoomSheet(
  videoZoom: Float,
  onSetVideoZoom: (Float) -> Unit,
  onResetVideoPan: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val defaultZoom by playerPreferences.defaultVideoZoom.collectAsState()
  val pinchToZoomGesture by playerPreferences.pinchToZoomGesture.collectAsState()
  val videoPanEnabled by playerPreferences.videoPanEnabled.collectAsState()
  var zoom by remember { mutableFloatStateOf(videoZoom) }

  val currentOnSetVideoZoom by rememberUpdatedState(onSetVideoZoom)

  LaunchedEffect(Unit) {
    val mpvZoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: videoZoom
    zoom = mpvZoom
  }

  LaunchedEffect(zoom) {
    currentOnSetVideoZoom(zoom)
  }

  PlayerSheet(onDismissRequest = onDismissRequest) {
    ZoomVideoSheet(
      zoom = zoom,
      defaultZoom = defaultZoom,
      zoomEnabled = pinchToZoomGesture,
      panEnabled = videoPanEnabled,
      onZoomChange = { newZoom -> zoom = newZoom },
      onSetAsDefault = {
        playerPreferences.defaultVideoZoom.set(zoom)
      },
      onReset = {
        zoom = 0f
        playerPreferences.defaultVideoZoom.set(0f)
        onResetVideoPan()
      },
      onZoomToggle = { enabled ->
        playerPreferences.pinchToZoomGesture.set(enabled)
      },
      onPanToggle = { enabled ->
        playerPreferences.videoPanEnabled.set(enabled)
        if (!enabled) {
          onResetVideoPan()
        }
      },
      modifier = modifier,
    )
  }
}

@Composable
private fun ZoomVideoSheet(
  zoom: Float,
  defaultZoom: Float,
  zoomEnabled: Boolean,
  panEnabled: Boolean,
  onZoomChange: (Float) -> Unit,
  onSetAsDefault: () -> Unit,
  onReset: () -> Unit,
  onZoomToggle: (Boolean) -> Unit,
  onPanToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isDefault = zoom == defaultZoom
  val isZero = zoom == 0f
  var showZoomInputDialog by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(vertical = MaterialTheme.spacing.medium),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    // Top row: Pinch to Zoom and Video Pan toggles
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Switch(
          checked = zoomEnabled,
          onCheckedChange = onZoomToggle,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.pref_player_gestures_pinch_to_zoom),
          style = MaterialTheme.typography.bodyMedium,
          color = if (zoomEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Switch(
          checked = panEnabled,
          onCheckedChange = onPanToggle,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Video Pan",
          style = MaterialTheme.typography.bodyMedium,
          color = if (panEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )

    // Middle row: Zoom slider with +/- buttons
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      FilledTonalIconButton(
        onClick = {
          val newZoom = (zoom - 0.05f).coerceAtLeast(-1f)
          onZoomChange(newZoom)
        },
        modifier = Modifier.size(36.dp),
      ) {
        Icon(Icons.Default.Remove, contentDescription = "Decrease zoom", modifier = Modifier.size(18.dp))
      }

      SliderItem(
        label = stringResource(id = R.string.player_sheets_zoom_slider_label),
        value = zoom,
        valueText = "%.2fx".format(zoom),
        onChange = onZoomChange,
        max = 3f,
        min = -1f,
        modifier = Modifier.weight(1f),
        onValueTextClick = { showZoomInputDialog = true },
      )

      FilledTonalIconButton(
        onClick = {
          val newZoom = (zoom + 0.05f).coerceAtMost(3f)
          onZoomChange(newZoom)
        },
        modifier = Modifier.size(36.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = "Increase zoom", modifier = Modifier.size(18.dp))
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )

    // Bottom row: Action buttons
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onSetAsDefault,
        enabled = !isDefault,
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(R.string.set_as_default), style = MaterialTheme.typography.labelMedium)
      }

      Button(
        onClick = onReset,
        enabled = !isZero,
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(R.string.generic_reset), style = MaterialTheme.typography.labelMedium)
      }
    }
  }

  // Manual zoom input dialog
  if (showZoomInputDialog) {
    ZoomInputDialog(
      currentZoom = zoom,
      onConfirm = { newZoom ->
        onZoomChange(newZoom)
        showZoomInputDialog = false
      },
      onDismiss = { showZoomInputDialog = false },
    )
  }
}

@Composable
private fun ZoomInputDialog(
  currentZoom: Float,
  onConfirm: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf("%.2f".format(currentZoom)) }
  var isError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }

  fun validate(input: String): Float? {
    val value = input.toFloatOrNull()
    if (value == null) {
      isError = true
      errorMessage = "Enter a valid number"
      return null
    }
    if (value < -1f || value > 3f) {
      isError = true
      errorMessage = "Zoom must be between -1.00 and 3.00"
      return null
    }
    isError = false
    errorMessage = ""
    return value
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Set Zoom Level") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = text,
          onValueChange = {
            text = it
            validate(it)
          },
          label = { Text("Zoom value (-1.00 to 3.00)") },
          isError = isError,
          supportingText = if (isError) {
            { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
          } else {
            null
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          keyboardActions = KeyboardActions(
            onDone = {
              val value = validate(text)
              if (value != null) onConfirm(value)
            },
          ),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val value = validate(text)
          if (value != null) onConfirm(value)
        },
      ) {
        Text("Apply")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
