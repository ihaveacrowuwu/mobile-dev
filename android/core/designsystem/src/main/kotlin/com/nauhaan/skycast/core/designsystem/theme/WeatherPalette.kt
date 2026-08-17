package com.nauhaan.skycast.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Weather-semantic colours, as a Material 3 **extended colour set**.
 *
 * Colour that encodes *meaning* rather than hierarchy belongs in a custom set alongside the scheme:
 * the `colorScheme` roles all come from one tonal palette, which under Material You is the user's
 * wallpaper, so a sun, a rain cloud and a barometer would render in near-identical tints.
 *
 * Every pair below is a container plus its `on` colour, chosen to clear WCAG AA (4.5:1) for body
 * text at the sizes used here, in both appearances.
 *
 * These stay fixed when Material You is on, so the wallpaper drives the app's chrome but not the
 * weather colours.
 */
@Immutable
data class WeatherPalette(
    val sunContainer: Color,
    val onSunContainer: Color,
    val moonContainer: Color,
    val onMoonContainer: Color,
    val cloudContainer: Color,
    val onCloudContainer: Color,
    val rainContainer: Color,
    val onRainContainer: Color,
    val drizzleContainer: Color,
    val onDrizzleContainer: Color,
    val thunderContainer: Color,
    val onThunderContainer: Color,
    val snowContainer: Color,
    val onSnowContainer: Color,
    val mistContainer: Color,
    val onMistContainer: Color,
    val humidity: Color,
    val wind: Color,
    val pressure: Color,
    val visibility: Color,
    val sunrise: Color,
    val sunset: Color,
    /** Track behind a metric indicator, the unfilled remainder. */
    val metricTrack: Color,
)

/**
 * Light appearance.
 *
 * Containers are soft washes so they sit quietly on a light surface; the `on` colours are deep and
 * saturated so the icon reads as a silhouette rather than a smudge.
 */
val LightWeatherPalette = WeatherPalette(
    sunContainer = Color(0xFFFFE7AE),
    onSunContainer = Color(0xFF7A4E00),
    moonContainer = Color(0xFFDCE1F5),
    onMoonContainer = Color(0xFF2B3768),
    cloudContainer = Color(0xFFE5E9F0),
    onCloudContainer = Color(0xFF3F4A5C),
    rainContainer = Color(0xFFD3E5FB),
    onRainContainer = Color(0xFF0F4478),
    drizzleContainer = Color(0xFFDBEFFA),
    onDrizzleContainer = Color(0xFF115B79),
    thunderContainer = Color(0xFFE8DDFB),
    onThunderContainer = Color(0xFF452A80),
    snowContainer = Color(0xFFE2F2F6),
    onSnowContainer = Color(0xFF0F5165),
    mistContainer = Color(0xFFE4E8E5),
    onMistContainer = Color(0xFF44504B),
    humidity = Color(0xFF1B6EC2),
    wind = Color(0xFF0F7A72),
    pressure = Color(0xFF6A4CA8),
    visibility = Color(0xFF0E7490),
    sunrise = Color(0xFFC9741A),
    sunset = Color(0xFFB4501F),
    metricTrack = Color(0x1A000000),
)

/**
 * Dark appearance.
 *
 * Containers are deep and desaturated so they do not glow against a dark surface; the `on` colours
 * are light tints of the same hue, which keeps each condition recognisable across both themes.
 */
val DarkWeatherPalette = WeatherPalette(
    sunContainer = Color(0xFF4A3812),
    onSunContainer = Color(0xFFFFD98A),
    moonContainer = Color(0xFF232949),
    onMoonContainer = Color(0xFFC2CAEE),
    cloudContainer = Color(0xFF2A303A),
    onCloudContainer = Color(0xFFC8D1E0),
    rainContainer = Color(0xFF113152),
    onRainContainer = Color(0xFFA8CCF2),
    drizzleContainer = Color(0xFF0F3F4E),
    onDrizzleContainer = Color(0xFFA5DBF0),
    thunderContainer = Color(0xFF32225B),
    onThunderContainer = Color(0xFFCCBAF5),
    snowContainer = Color(0xFF113945),
    onSnowContainer = Color(0xFFADDFEB),
    mistContainer = Color(0xFF292F2B),
    onMistContainer = Color(0xFFC2CBC5),
    humidity = Color(0xFF7FB6EE),
    wind = Color(0xFF62C8BF),
    pressure = Color(0xFFB9A2EE),
    visibility = Color(0xFF6FC5DE),
    sunrise = Color(0xFFF0B267),
    sunset = Color(0xFFE58A63),
    metricTrack = Color(0x22FFFFFF),
)

/**
 * The palette in scope.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: it changes only when the theme
 * flips, so there is no benefit to tracking reads individually and a real cost to doing so.
 */
val LocalWeatherPalette = staticCompositionLocalOf { LightWeatherPalette }

/** Shorthand, mirroring how `MaterialTheme.colorScheme` is read. */
val weatherPalette: WeatherPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalWeatherPalette.current
