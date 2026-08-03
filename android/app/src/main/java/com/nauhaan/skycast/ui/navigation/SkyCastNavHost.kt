package com.nauhaan.skycast.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
fun SkyCastNavHost(navigator: SkyCastNavigator, modifier: Modifier = Modifier) {
    NavHost(
        navController = navigator.navController,
        startDestination = Route.Today,
        modifier = modifier,
        // Horizontal slide for pushes reads as "deeper in"; tab switches cross-fade
        // because they are lateral moves, not descents.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_MILLIS),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_MILLIS),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_MILLIS),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_MILLIS),
            )
        },
    ) {
        // ── Tabs ───────────────────────────────────────────────────────────

        composable<Route.Today>(
            enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
        ) {
            TodayScreen(
                onNavigateToLocationDetail = navigator::navigateToLocationDetail,
                onNavigateToAddLocation = navigator::navigateToAddLocation,
            )
        }

        composable<Route.Forecast>(
            enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
        ) {
            ForecastScreen(onNavigateToDayDetail = navigator::navigateToDayDetail)
        }

        composable<Route.Locations>(
            enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
        ) {
            LocationsScreen(
                onNavigateToAddLocation = navigator::navigateToAddLocation,
                onNavigateToLocationDetail = navigator::navigateToLocationDetail,
            )
        }

        composable<Route.Settings>(
            enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
        ) {
            SettingsScreen(onNavigateToAbout = navigator::navigateToAbout)
        }

        // ── Pushed destinations ────────────────────────────────────────────

        composable<Route.LocationDetail> { backStackEntry ->
            // toRoute() deserialises the typed arguments, no string keys, no casts.
            val route: Route.LocationDetail = backStackEntry.toRoute()
            LocationDetailScreen(
                locationId = route.locationId,
                onNavigateBack = navigator::navigateBack,
            )
        }

        composable<Route.DayDetail> { backStackEntry ->
            val route: Route.DayDetail = backStackEntry.toRoute()
            DayDetailScreen(
                locationId = route.locationId,
                epochDay = route.epochDay,
                onNavigateBack = navigator::navigateBack,
            )
        }

        composable<Route.AddLocation> {
            AddLocationScreen(onNavigateBack = navigator::navigateBack)
        }

        composable<Route.About> {
            AboutScreen(onNavigateBack = navigator::navigateBack)
        }
    }
}

private const val TRANSITION_MILLIS = 250
