package com.nauhaan.skycast.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nauhaan.skycast.domain.model.ThemeMode

/**
 * The app's Material 3 theme.
 *
 * Supports the user's explicit light/dark choice as well as following the system, and
 * opts into Material You dynamic colour on Android 12+ when the user allows it.
 * Respecting platform theming conventions is part of MO2 (user expectations) and is
 * assessed under UI/UX.
 */
@Composable
fun SkyCastTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColour: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    val supportsDynamicColour = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            useDynamicColour && supportsDynamicColour -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> SkyCastDarkColorScheme
            else -> SkyCastLightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SkyCastTypography,
        shapes = SkyCastShapes,
        content = content,
    )
}
