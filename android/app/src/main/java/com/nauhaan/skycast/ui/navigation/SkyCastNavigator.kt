package com.nauhaan.skycast.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Wraps the [NavHostController] so screens never touch it directly.
 *
 * Two reasons this exists:
 *
 * 1. **Testability and previewability**, screens take `() -> Unit` lambdas, so they
 *    render in `@Preview` and in Compose tests with no navigation graph present.
 * 2. **Correct tab behaviour in one place**, the `saveState`/`restoreState` and
 *    `launchSingleTop` flags that make tab switching preserve each tab's scroll
 *    position and back stack are easy to get subtly wrong, so they are written once
 *    here rather than at every call site.
 */
class SkyCastNavigator(val navController: NavHostController) {
    /**
     * Switches to a top-level tab.
     *
     * `popUpTo(startDestination) { saveState = true }` plus `restoreState = true` is
     * what gives each tab an independent, remembered stack: leave Forecast scrolled
     * to day four, visit Settings, come back, and it is still on day four.
     */
    fun navigateToTab(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            // Re-tapping the current tab must not stack a second copy of it.
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToLocationDetail(locationId: Long) {
        navController.navigate(Route.LocationDetail(locationId))
    }

    fun navigateToDayDetail(locationId: Long, epochDay: Long) {
        navController.navigate(Route.DayDetail(locationId, epochDay))
    }

    fun navigateToAddLocation() {
        navController.navigate(Route.AddLocation)
    }

    fun navigateToAbout() {
        navController.navigate(Route.About)
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberSkyCastNavigator(navController: NavHostController = rememberNavController()): SkyCastNavigator =
    remember(navController) { SkyCastNavigator(navController) }

/**
 * The tab currently in the foreground, or `null` when a pushed destination is showing.
 *
 * Returning `null` is deliberate: it lets the bottom bar hide itself on detail screens
 * instead of highlighting a tab the user is not really on.
 */
@Composable
fun SkyCastNavigator.currentTopLevelDestination(): TopLevelDestination? {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination ?: return null
    return TopLevelDestination.entries.firstOrNull { tab ->
        when (tab.route) {
            Route.Today -> destination.hasRoute(Route.Today::class)
            Route.Forecast -> destination.hasRoute(Route.Forecast::class)
            Route.Locations -> destination.hasRoute(Route.Locations::class)
            Route.Settings -> destination.hasRoute(Route.Settings::class)
            else -> false
        }
    }
}
