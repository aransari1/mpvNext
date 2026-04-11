package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteConfirmationDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  itemType: String,
  itemCount: Int,
  itemNames: List<String> = emptyList(),
  totalSize: Long = 0L,
) {
  if (!isOpen) return

  val itemText = if (itemCount == 1) itemType else "${itemType}s"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Delete $itemCount $itemText?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ),
          shape = MaterialTheme.shapes.extraLarge,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "This action cannot be undone. The selected item${if (itemCount == 1) "" else "s"} will be permanently deleted.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
          )
        }

        if (itemNames.isNotEmpty()) {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
              ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
            ) {
              val scrollState = rememberScrollState()

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 200.dp)
                  .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                itemNames.forEachIndexed { index, name ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                  ) {
                    Text(
                      text = "${index + 1}. ",
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = name,
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (totalSize > 0L) {
          Text(
            text = formatFileSize(totalSize),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
          onClick = onDismiss,
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Text("Cancel", fontWeight = FontWeight.Medium)
        }
        Button(
          onClick = {
            onConfirm()
            onDismiss()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Text(
            text = "Delete",
            fontWeight = FontWeight.Bold,
          )
        }
      }
    },
    dismissButton = {},
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}

private fun formatFileSize(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val units = arrayOf("B", "KB", "MB", "GB", "TB")
  val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
  val value = bytes / 1024.0.pow(digitGroups.toDouble())
  return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
}
