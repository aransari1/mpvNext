package xyz.mpv.rex.ui.utils

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for issue #459: "NavDisplay backstack cannot be empty".
 *
 * `NavDisplay` throws when its back stack is empty, so popping must never remove the root entry.
 */
class NavBackStackExtTest {

  private object RootScreen : NavKey
  private object ChildScreen : NavKey

  @Test
  fun `does not pop the root entry`() {
    val backStack = NavBackStack<NavKey>(RootScreen)

    assertFalse(backStack.navigateBack())
    assertEquals(listOf<NavKey>(RootScreen), backStack.toList())
  }

  @Test
  fun `does not pop an already empty back stack`() {
    val backStack = NavBackStack<NavKey>()

    assertFalse(backStack.navigateBack())
    assertTrue(backStack.isEmpty())
  }

  @Test
  fun `pops the top entry when a child screen is present`() {
    val backStack = NavBackStack<NavKey>(RootScreen, ChildScreen)

    assertTrue(backStack.navigateBack())
    assertEquals(listOf<NavKey>(RootScreen), backStack.toList())
  }

  @Test
  fun `repeated back never empties the back stack`() {
    val backStack = NavBackStack<NavKey>(RootScreen, ChildScreen, ChildScreen)

    repeat(5) { backStack.navigateBack() }

    assertEquals(listOf<NavKey>(RootScreen), backStack.toList())
  }
}
