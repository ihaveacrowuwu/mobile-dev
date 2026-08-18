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
import com.nauhaan.skycast.ui.navigation.SkyCastNavHost
import com.nauhaan.skycast.ui.navigation.TopLevelDestination
import com.nauhaan.skycast.ui.navigation.currentTopLevelDestination
import com.nauhaan.skycast.ui.navigation.rememberSkyCastNavigator

/**
 * The app shell: bottom navigation bar plus the navigation graph.
 *
 * The bar hides itself on pushed destinations (detail screens, search) because the user
 * is no longer "on" a tab there and highlighting one would be misleading. That is also
 * why [currentTopLevelDestination] is nullable.
 *
 * ## The bar joins in on Today
 *
 * Today paints the whole screen with the condition's mood, and a navigation bar that stayed a flat
 * theme surface underneath it read as a different app stuck to the bottom. It therefore takes the
 * same wash, but **only on Today**, because that is the only tab that is about one place's weather
 * right now. A list of saved cities has no single condition to reflect, and tinting it would be
 * decoration rather than information.
 *
 * The tint arrives by lambda from `TodayScreen`: the bar is a sibling of the content, not a
 * descendant, so the CompositionLocal the tiles read cannot reach it.
 */
@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val navigator = rememberSkyCastNavigator()
    val currentTab = navigator.currentTopLevelDestination()

    var todayTint by remember { mutableStateOf<Color?>(null) }
    val base = NavigationBarDefaults.containerColor
    val tint = todayTint.takeIf { currentTab == TopLevelDestination.TODAY }
    // Effects spec, not spatial: this is a colour, and a spatial spec would overshoot past the
    // target colour on the way to it.
    val barContainer by animateColorAsState(
        targetValue = tint?.copy(alpha = NAV_BAR_TINT_ALPHA)?.compositeOver(base) ?: base,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navigationBarContainer",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
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

/**
 * Lighter than the tint on the detail tiles, so the selected-item indicator and the four labels
 * stay legible.
 */
private const val NAV_BAR_TINT_ALPHA = 0.25f
