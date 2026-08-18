package com.nauhaan.skycast.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nauhaan.skycast.ui.detail.DayDetailScreen
import com.nauhaan.skycast.ui.detail.LocationDetailScreen
import com.nauhaan.skycast.ui.forecast.ForecastScreen
import com.nauhaan.skycast.ui.locations.AddLocationScreen
import com.nauhaan.skycast.ui.locations.LocationsScreen
import com.nauhaan.skycast.ui.settings.AboutScreen
import com.nauhaan.skycast.ui.settings.SettingsScreen
import com.nauhaan.skycast.ui.today.TodayScreen

/**
 * The app's single navigation graph.
 *
 * Tabs are the four top-level destinations; pushed destinations layer on top of
 * whichever tab opened them, so back always returns to where the user came from. The
 * back stack for each tab is preserved by [SkyCastNavigator], which uses
 * `saveState`/`restoreState` when switching tabs.
 *
 * Screens receive plain lambdas for navigation rather than the [NavHostController]
 * itself. That keeps every screen previewable and independently testable, a screen
 * that owns a controller cannot be rendered in a `@Preview`.
 */
@Composable
fun SkyCastNavHost(
    navigator: SkyCastNavigator,
    modifier: Modifier = Modifier,
    onTodayWeatherTintChanged: (Color?) -> Unit = {},
) {
    // Read the Expressive motion scheme once, here, where we are still in a @Composable.
    // The transition lambdas below run in AnimatedContentTransitionScope, which is not
    // composable, so MaterialTheme cannot be read inside them.
    //
    // Spatial spec for the slide (geometry, where overshoot is desirable) and effects spec for
    // the fade (opacity, which must NOT overshoot past fully opaque).
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    NavHost(
        navController = navigator.navController,
        startDestination = Route.Today,
        modifier = modifier,
        // Horizontal slide for pushes reads as "deeper in"; tab switches cross-fade
        // because they are lateral moves, not descents.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = slideSpec,
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = slideSpec,
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = slideSpec,
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = slideSpec,
            )
        },
    ) {
        // ── Tabs ───────────────────────────────────────────────────────────

        composable<Route.Today>(
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
        ) {
            TodayScreen(
                onNavigateToLocationDetail = navigator::navigateToLocationDetail,
                onNavigateToAddLocation = navigator::navigateToAddLocation,
                onWeatherTintChanged = onTodayWeatherTintChanged,
            )
        }

        composable<Route.Forecast>(
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
        ) {
            ForecastScreen(onNavigateToDayDetail = navigator::navigateToDayDetail)
        }

        composable<Route.Locations>(
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
        ) {
            LocationsScreen(
                onNavigateToAddLocation = navigator::navigateToAddLocation,
                onNavigateToLocationDetail = navigator::navigateToLocationDetail,
            )
        }

        composable<Route.Settings>(
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
        ) {
            SettingsScreen(onNavigateToAbout = navigator::navigateToAbout)
        }

        // ── Pushed destinations ────────────────────────────────────────────

        // Neither pushed destination is handed its arguments here. Both view models read the
        // typed route from their own SavedStateHandle via toRoute(), so argument decoding lives
        // with the code that needs it and the composables stay previewable.
        composable<Route.LocationDetail> {
            LocationDetailScreen(onNavigateBack = navigator::navigateBack)
        }

        composable<Route.DayDetail> {
            DayDetailScreen(onNavigateBack = navigator::navigateBack)
        }

        composable<Route.AddLocation> {
            AddLocationScreen(onNavigateBack = navigator::navigateBack)
        }

        composable<Route.About> {
            AboutScreen(onNavigateBack = navigator::navigateBack)
        }
    }
}
