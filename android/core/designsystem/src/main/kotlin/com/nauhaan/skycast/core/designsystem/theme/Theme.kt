package com.nauhaan.skycast.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nauhaan.skycast.domain.model.ThemeMode

/**
 * The app's **Material 3 Expressive** theme.
 *
 * `MaterialExpressiveTheme`, not `MaterialTheme`. Opting into Expressive changes three things:
 *
 * 1. **Motion**: components animate with the expressive motion scheme (springier, more overshoot)
 *    instead of the standard one. Read it back through `MaterialTheme.motionScheme` so custom
 *    animations match the built-in components.
 * 2. **Shape**: a more varied, less uniform corner language (see [SkyCastShapes]).
 * 3. **Typography**: the *emphasized* type roles become available, which the hero temperature uses.
 *
 * `motionScheme` is **not** passed. `MotionScheme.expressive()` is internal to the library, so
 * omitting the parameter is how you get it, and `MaterialTheme.motionScheme` reads it afterwards.
 *
 * Supports the user's explicit light/dark choice as well as following the system, and opts into
 * Material You dynamic colour on Android 12+ when the user allows it.
 */
@Composable
fun SkyCastTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColour: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val supportsDynamicColour = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColour && supportsDynamicColour -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> SkyCastDarkColorScheme
        else -> SkyCastLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = SkyCastShapes,
        typography = SkyCastTypography,
        content = content,
    )
}
