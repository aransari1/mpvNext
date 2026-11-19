package xyz.mpv.rex.ui.browser.states

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The organic silhouette behind the icon in an empty or loading state.
 *
 * Empty states are the one place in this app where a decorative shape costs nothing: there is no
 * data to read and no control to hit. Anywhere denser — cards, buttons, the nav bar — scalloped
 * edges would compete with the content instead.
 *
 * The silhouette is static by design. Empty states sit still while the user reads them; motion
 * belongs to the loading spinner, which uses `LoadingIndicator` and morphs between these same
 * polygons. (`rememberAnimatedShape` is internal in material3 1.5.0-alpha15, so hand-rolling the
 * morph here would need private API.)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StateIconSurface(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.size(96.dp),
    shape = MaterialShapes.Cookie9Sided.toShape(),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tonalElevation = 0.dp,
    content = content,
  )
}
