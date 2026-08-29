package com.nauhaan.skycast.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.core.designsystem.component.NightSkyHorizon
import com.nauhaan.skycast.core.designsystem.component.WeatherBackground
import com.nauhaan.skycast.core.designsystem.component.nightSky
import com.nauhaan.skycast.core.designsystem.theme.NightSkyTheme
import com.nauhaan.skycast.core.designsystem.theme.weatherSurfaceTint
import com.nauhaan.skycast.ui.navigation.SkyCastNavHost
import com.nauhaan.skycast.ui.navigation.TopLevelDestination
import com.nauhaan.skycast.ui.navigation.currentTopLevelDestination
import com.nauhaan.skycast.ui.navigation.rememberSkyCastNavigator

/**
 * The app shell: bottom navigation bar plus the navigation graph.
 *
 * The bar hides itself on pushed destinations (detail screens, search), which is why
 * [currentTopLevelDestination] is nullable.
 *
 * The bar takes Today's condition wash, and only on Today. The tint arrives by lambda from
 * `HomeScreen`, because the bar is a sibling of the content rather than a descendant, so the
 * CompositionLocal the tiles read cannot reach it.
 */
@Composable
fun RootScreen(modifier: Modifier = Modifier, backgroundViewModel: AppBackgroundViewModel = hiltViewModel()) {
    val navigator = rememberSkyCastNavigator()
    val currentTab = navigator.currentTopLevelDestination()
    val background by backgroundViewModel.background.collectAsStateWithLifecycle()

    var todayTint by remember { mutableStateOf<Color?>(null) }
    val base = NavigationBarDefaults.containerColor
    // The Moon tab replaces the weather background with a night sky, so the bar belongs to that
    // sky rather than to the favourite's weather.
    val onNightSky = currentTab == TopLevelDestination.MOON

    SystemBarIcons(light = !onNightSky && MaterialTheme.colorScheme.surface.luminance() > MID_LUMINANCE)

    // On Home the bar shares the page you are looking at; everywhere else it shares the favourite's,
    // which is what the background behind it is drawing too.
    val tint = todayTint.takeIf { currentTab == TopLevelDestination.HOME }
        ?: weatherSurfaceTint(background.condition, background.isDaytime)
    // Effects spec, not spatial: this is a colour, and a spatial spec would overshoot past the
    // target colour on the way to it.
    val barContainer by animateColorAsState(
        targetValue = when {
            // The sky's own colour where it meets the bottom of the screen.
            onNightSky -> NightSkyHorizon
            else -> tint?.copy(alpha = NAV_BAR_TINT_ALPHA)?.compositeOver(base) ?: base
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navigationBarContainer",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Transparent, so the weather background below shows through the shell rather than being
        // covered by the theme's surface colour.
        containerColor = Color.Transparent,
        // `contentColorFor(Transparent)` can't resolve against the scheme, so without this the
        // Scaffold falls back to Material3's ambient default, black, leaving text and icons
        // unreadable in dark mode.
        contentColor = MaterialTheme.colorScheme.onSurface,
        bottomBar = {
            AnimatedVisibility(visible = currentTab != null) {
                // Dark-themed on the Moon tab, and not only for the container: the labels and the
                // selected-item indicator come from the colour scheme, so a dark bar with the light
                // theme's scheme puts near-black text on it. NightSkyTheme fixes all of them together.
                NightSkyTheme(enabled = onNightSky) {
                    NavigationBar(containerColor = barContainer) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = destination == currentTab
                            NavigationBarItem(
                                modifier = Modifier.testTag(destination.testTag),
                                selected = selected,
                                onClick = { navigator.navigateToTab(destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        },
                                        // Null: NavigationBarItem already exposes the label
                                        // to accessibility services, so labelling the icon
                                        // too makes TalkBack say everything twice.
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        // The favourite's weather, behind the whole graph. Home draws its own over the top of it;
        // every other screen sits on it. See AppBackgroundViewModel.
        //
        // The Moon tab's sky is painted here, over the weather and *outside* `innerPadding`, so it
        // reaches the status bar and the navigation bar instead of stopping at the content area.
        WeatherBackground(
            condition = background.condition,
            isDaytime = background.isDaytime,
            modifier = Modifier.fillMaxSize(),
        ) {
            SkyCastNavHost(
                navigator = navigator,
                modifier = Modifier
                    // Over the weather and *outside* the padding, so the Moon tab's sky reaches the
                    // status bar and the navigation bar. `WeatherBackground` paints its own wash in a
                    // `drawBehind`, so a sky added to its modifier would be painted first and then
                    // covered; it has to be a layer inside it.
                    .then(if (onNightSky) Modifier.nightSky() else Modifier)
                    .padding(innerPadding)
                    // `padding` applies the insets but does not mark them handled, so a pushed
                    // screen's own Scaffold adds the status-bar inset a second time and its app bar
                    // sits a status bar's height too low. `consumeWindowInsets` is what tells
                    // descendants these are already accounted for.
                    .consumeWindowInsets(innerPadding),
                onTodayWeatherTintChanged = { todayTint = it },
            )
        }
    }
}

/**
 * Matches the system-bar icons to whatever is actually behind them.
 *
 * `enableEdgeToEdge` takes its default from the *system's* dark mode, which is not the same
 * question: the app can be in its dark theme on a light phone, and the Moon tab is a night sky in
 * either theme.
 *
 * Derived from the resolved surface colour, so dynamic colour and the system-following mode are
 * both covered without threading the preference down here.
 */
@Composable
private fun SystemBarIcons(light: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    if (view.isInEditMode || window == null) return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

/** Above this, a surface is light enough to need dark icons on it. */
private const val MID_LUMINANCE = 0.5f

/**
 * Lighter than the tint on the detail tiles, so the selected-item indicator and the four labels
 * stay legible.
 */
private const val NAV_BAR_TINT_ALPHA = 0.25f
