package com.nauhaan.skycast.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.core.designsystem.component.WeatherBackground
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
    // On Home the bar shares the page you are looking at; everywhere else it shares the favourite's,
    // which is what the background behind it is drawing too.
    val tint = todayTint.takeIf { currentTab == TopLevelDestination.HOME }
        ?: weatherSurfaceTint(background.condition, background.isDaytime)
    // Effects spec, not spatial: this is a colour, and a spatial spec would overshoot past the
    // target colour on the way to it.
    val barContainer by animateColorAsState(
        targetValue = tint?.copy(alpha = NAV_BAR_TINT_ALPHA)?.compositeOver(base) ?: base,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navigationBarContainer",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Transparent, so the weather background below shows through the shell rather than being
        // covered by the theme's surface colour.
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(visible = currentTab != null) {
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
        },
    ) { innerPadding ->
        // The favourite's weather, behind the whole graph. Home and the Moon tab draw their own over
        // the top of it; every other screen simply sits on it. See AppBackgroundViewModel.
        WeatherBackground(
            condition = background.condition,
            isDaytime = background.isDaytime,
            modifier = Modifier.fillMaxSize(),
        ) {
            SkyCastNavHost(
                navigator = navigator,
                modifier = Modifier
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
 * Lighter than the tint on the detail tiles, so the selected-item indicator and the four labels
 * stay legible.
 */
private const val NAV_BAR_TINT_ALPHA = 0.25f
