package com.nauhaan.skycast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 */
@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val navigator = rememberSkyCastNavigator()
    val currentTab = navigator.currentTopLevelDestination()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(visible = currentTab != null) {
                NavigationBar {
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
            modifier = Modifier.padding(innerPadding),
        )
    }
}
