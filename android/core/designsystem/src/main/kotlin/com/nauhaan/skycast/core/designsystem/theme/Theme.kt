package com.nauhaan.skycast.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

    // The weather palette follows light/dark but ignores dynamic colour, so the wallpaper drives
    // the app's chrome but not the weather colours. See WeatherPalette.
    val weatherPalette = if (darkTheme) DarkWeatherPalette else LightWeatherPalette

    CompositionLocalProvider(LocalWeatherPalette provides weatherPalette) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = SkyCastShapes,
            typography = SkyCastTypography,
            content = content,
        )
    }
}

/**
 * The dark scheme, forced, for a screen that is a night sky whichever theme the phone is in.
 *
 * The Moon tab's background is a starfield, it has to be dark, because moonlight only reads as
 * moonlight against a dark ground. That makes every colour role inside it wrong in the light theme:
 * `onSurface` would be near-black text on a near-black sky, and the frosted cards would try to be pale.
 *
 * Providing the dark scheme for that subtree fixes all of them at once, and is why this is a theme
 * rather than a handful of hardcoded white text colours. Dynamic colour is deliberately skipped: the
 * user's wallpaper has no say over what the night sky looks like.
 */
@Composable
fun NightSkyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWeatherPalette provides DarkWeatherPalette,
        // `LocalContentColor` too, and it is not redundant: `MaterialTheme` sets the *scheme*, while the
        // colour an unstyled `Text` actually uses comes from the nearest `Surface`. With no Surface
        // between here and the shell, the section headings inherited the light theme's near-black
        // `onSurface` and were unreadable against the night sky, visible immediately in a screenshot.
        LocalContentColor provides SkyCastDarkColorScheme.onSurface,
    ) {
        MaterialExpressiveTheme(
            colorScheme = SkyCastDarkColorScheme,
            shapes = SkyCastShapes,
            typography = SkyCastTypography,
            content = content,
        )
    }
}
