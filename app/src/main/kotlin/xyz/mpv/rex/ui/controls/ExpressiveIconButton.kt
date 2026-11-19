package xyz.mpv.rex.ui.controls

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The tonal families an expressive icon button can take.
 *
 * Before this existed, 22 call sites hand-rolled `IconButtonDefaults.filledTonalIconButtonColors`
 * with a container/content pair, six of them identically in the browser's selection bar. Nothing
 * ever varied the tones themselves, only which pair was chosen — so the pair belongs here, named.
 */
enum class TonalIconTone {
  /** Neutral fill for ordinary actions. */
  Secondary,

  /** Emphasised fill for the primary action in a cluster. */
  Primary,

  /** Destructive actions. */
  Error,

  /**
   * Active state of a toggle whose inactive state is [Muted].
   *
   * The fill is [MaterialTheme.colorScheme.primaryContainer] with a
   * [MaterialTheme.colorScheme.primary] glyph rather than `onPrimaryContainer`: the accent
   * glyph is what makes an enabled shuffle/repeat read as switched on.
   */
  Active,

  /** Muted fill for low-emphasis toggles on a busy surface. */
  Muted,
}

/**
 * Colours for a flat [TonalIconTone], resolved against the active theme.
 *
 * [Muted] used to be inlined as `surfaceContainerHighest.copy(alpha = 0.5f)`; keep that exact
 * value so existing screens do not shift tone.
 */
@Composable
fun tonalIconButtonColors(tone: TonalIconTone): IconButtonColors {
  val scheme = MaterialTheme.colorScheme
  return when (tone) {
    TonalIconTone.Secondary -> IconButtonDefaults.filledTonalIconButtonColors(
      containerColor = scheme.secondaryContainer,
      contentColor = scheme.onSecondaryContainer,
    )
    TonalIconTone.Primary -> IconButtonDefaults.filledTonalIconButtonColors(
      containerColor = scheme.primaryContainer,
      contentColor = scheme.onPrimaryContainer,
    )
    TonalIconTone.Error -> IconButtonDefaults.filledTonalIconButtonColors(
      containerColor = scheme.errorContainer,
      contentColor = scheme.onErrorContainer,
    )
    TonalIconTone.Active -> IconButtonDefaults.filledTonalIconButtonColors(
      containerColor = scheme.primaryContainer,
      contentColor = scheme.primary,
    )
    TonalIconTone.Muted -> IconButtonDefaults.filledTonalIconButtonColors(
      containerColor = scheme.surfaceContainerHighest.copy(alpha = 0.5f),
      contentColor = scheme.onSurface,
    )
  }
}

/**
 * Shape pair for an icon button that morphs toward its pressed silhouette while held.
 *
 * This is [IconButtonDefaults.shapes] unchanged: `CircleShape` resting, pressed shape from
 * `MaterialTheme.shapes.small`.
 *
 * **Size caveat.** The pressed shape is a fixed corner radius (10.dp in this app's `AppShapes`),
 * so the morph reads as "round" at the stock 40dp icon-button size but as a *square* on larger
 * buttons — the mini player's 46/48dp controls did exactly that. Those use press **scale**
 * instead (see `MiniPlayerIconButton`). Keep the morph for ~40dp controls only.
 *
 * Callers still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun iconButtonShapes(): IconButtonShapes = IconButtonDefaults.shapes()
