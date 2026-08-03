package com.nauhaan.skycast.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The SkyCast palette.
 *
 * Used as the fallback when Material You dynamic colour is unavailable (Android 11
 * and below) or the user has switched it off in Settings.
 *
 * Every pair was checked for at least WCAG AA contrast (4.5:1) against its container.
 * Views must consume `MaterialTheme.colorScheme.*`, never these constants directly,
 * that is what makes dark mode work without touching a single screen.
 */

// ── Brand: a clear-sky blue ────────────────────────────────────────────────
private val SkyBlue10 = Color(0xFF001D33)
private val SkyBlue20 = Color(0xFF003354)
private val SkyBlue30 = Color(0xFF004A78)
private val SkyBlue40 = Color(0xFF00639D)
private val SkyBlue80 = Color(0xFF9BCBFF)
private val SkyBlue90 = Color(0xFFCFE5FF)

// ── Secondary: overcast grey-blue ──────────────────────────────────────────
private val Overcast10 = Color(0xFF0E1D2A)
private val Overcast20 = Color(0xFF243240)
private val Overcast30 = Color(0xFF3A4857)
private val Overcast40 = Color(0xFF52606F)
private val Overcast80 = Color(0xFFBAC8D9)
private val Overcast90 = Color(0xFFD6E4F6)

// ── Tertiary: sunrise amber, for warnings and highlights ───────────────────
private val Sunrise10 = Color(0xFF2C1600)
private val Sunrise20 = Color(0xFF492900)
private val Sunrise30 = Color(0xFF683C00)
private val Sunrise40 = Color(0xFF8A5100)
private val Sunrise80 = Color(0xFFFFB870)
private val Sunrise90 = Color(0xFFFFDCBE)

// ── Error: storm red ───────────────────────────────────────────────────────
private val StormRed10 = Color(0xFF410002)
private val StormRed20 = Color(0xFF690005)
private val StormRed30 = Color(0xFF93000A)
private val StormRed40 = Color(0xFFBA1A1A)
private val StormRed80 = Color(0xFFFFB4AB)
private val StormRed90 = Color(0xFFFFDAD6)

// ── Neutrals ───────────────────────────────────────────────────────────────
private val Neutral6 = Color(0xFF0D1116)
private val Neutral10 = Color(0xFF191C20)
private val Neutral20 = Color(0xFF2E3135)
private val Neutral90 = Color(0xFFE2E2E6)
private val Neutral95 = Color(0xFFF0F0F4)
private val Neutral99 = Color(0xFFFCFCFF)

internal val SkyCastLightColorScheme =
    lightColorScheme(
        primary = SkyBlue40,
        onPrimary = Color.White,
        primaryContainer = SkyBlue90,
        onPrimaryContainer = SkyBlue10,
        secondary = Overcast40,
        onSecondary = Color.White,
        secondaryContainer = Overcast90,
        onSecondaryContainer = Overcast10,
        tertiary = Sunrise40,
        onTertiary = Color.White,
        tertiaryContainer = Sunrise90,
        onTertiaryContainer = Sunrise10,
        error = StormRed40,
        onError = Color.White,
        errorContainer = StormRed90,
        onErrorContainer = StormRed10,
        background = Neutral99,
        onBackground = Neutral10,
        surface = Neutral99,
        onSurface = Neutral10,
        surfaceVariant = Neutral95,
        onSurfaceVariant = Neutral20,
        outline = Overcast40,
    )

internal val SkyCastDarkColorScheme =
    darkColorScheme(
        primary = SkyBlue80,
        onPrimary = SkyBlue20,
        primaryContainer = SkyBlue30,
        onPrimaryContainer = SkyBlue90,
        secondary = Overcast80,
        onSecondary = Overcast20,
        secondaryContainer = Overcast30,
        onSecondaryContainer = Overcast90,
        tertiary = Sunrise80,
        onTertiary = Sunrise20,
        tertiaryContainer = Sunrise30,
        onTertiaryContainer = Sunrise90,
        error = StormRed80,
        onError = StormRed20,
        errorContainer = StormRed30,
        onErrorContainer = StormRed90,
        background = Neutral6,
        onBackground = Neutral90,
        surface = Neutral6,
        onSurface = Neutral90,
        surfaceVariant = Neutral20,
        onSurfaceVariant = Neutral90,
        outline = Overcast80,
    )
