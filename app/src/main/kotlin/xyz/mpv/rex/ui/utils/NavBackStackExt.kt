package xyz.mpv.rex.ui.utils

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Safely pops the top entry off the navigation back stack.
 *
 * `NavDisplay` requires its back stack to never be empty: if the last entry is removed it
 * throws `IllegalArgumentException: NavDisplay backstack cannot be empty` during the next
 * recomposition. Every "back"/"up" action in the app must therefore go through this helper
 * instead of calling `removeLastOrNull()` directly.
 *
 * The root entry (the last remaining screen) is never popped.
 *
 * @return true when an entry was removed, false when the stack was already at its root.
 */
fun <T : NavKey> NavBackStack<T>.navigateBack(): Boolean {
  if (size <= 1) return false
  return removeLastOrNull() != null
}
