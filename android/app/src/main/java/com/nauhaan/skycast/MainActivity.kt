package com.nauhaan.skycast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.ui.RootScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single activity. Compose owns everything inside it.
 *
 * One activity, many composable destinations, is the modern Android pattern and the
 * structural counterpart to a single `WindowGroup` on iOS.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installed before super.onCreate so the splash screen can be held while the
        // stored theme preference loads. Without this the app flashes the wrong theme.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { viewModel.isLoadingTheme.value }

        // Draw behind the system bars; individual screens apply window insets.
        enableEdgeToEdge()

        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()

            SkyCastTheme(
                themeMode = themeState.themeMode,
                useDynamicColour = themeState.useDynamicColour,
            ) {
                RootScreen()
            }
        }
    }
}
