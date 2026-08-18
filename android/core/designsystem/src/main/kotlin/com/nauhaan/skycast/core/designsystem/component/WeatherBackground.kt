package com.nauhaan.skycast.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nauhaan.skycast.core.designsystem.theme.LocalWeatherTint
import com.nauhaan.skycast.core.designsystem.theme.weatherTint
import com.nauhaan.skycast.domain.model.WeatherCondition

/**
 * A background that reflects the current condition and time of day.
 *
 * Paints the theme's own `surface` and lays a **low-alpha wash of the condition's hue** over it, so
 * the base is always the colour the theme guarantees text contrast against and the condition shifts
 * the mood rather than replacing the palette.
 *
 * The wash drifts slowly, at eighteen seconds per sweep, and stops entirely when the user has asked
 * for less motion.
 */
@Composable
fun WeatherBackground(
    condition: WeatherCondition,
    isDaytime: Boolean,
    modifier: Modifier = Modifier,
    intensity: BackgroundIntensity = BackgroundIntensity.FULL,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val tint = weatherTint(condition, isDaytime)
    val drift = animatedDrift()

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(surface)

                // Two washes: a broad vertical one for the overall mood, and a soft radial glow
                // standing in for where the light is coming from. The radial one is what moves.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = intensity.topAlpha),
                            tint.copy(alpha = intensity.midAlpha),
                            // Settles to a trace, so the bottom of the screen, where the navigation
                            // bar sits, still belongs to the page.
                            tint.copy(alpha = intensity.floorAlpha),
                        ),
                    ),
                )
                // Both the position and the strength move. Position alone was imperceptible:
                // a wide, soft glow sliding across a wide, soft gradient changes any given pixel
                // by about one value in 255, which is not an animation, it is a rounding error.
                val glowAlpha = intensity.glowAlpha * (GLOW_ALPHA_MIN + drift * GLOW_ALPHA_TRAVEL)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(
                            x = size.width * (GLOW_X_MIN + drift * GLOW_X_TRAVEL),
                            y = size.height * GLOW_Y,
                        ),
                        radius = size.width * GLOW_RADIUS_RATIO,
                    ),
                )
            },
    ) {
        // Published to the content in front, not just painted behind it: the detail tiles and any
        // other surface on the page mix a little of it into their own fill.
        CompositionLocalProvider(LocalWeatherTint provides tint) {
            content()
        }
    }
}

/**
 * How strongly the background reads.
 *
 * Today gets the full treatment because it is the screen about the weather right now. Everything
 * else gets a whisper of the same hue: enough that the app feels like one place, not so much that a
 * list of saved cities competes with the forecast for attention.
 */
enum class BackgroundIntensity(
    val topAlpha: Float,
    val midAlpha: Float,
    val glowAlpha: Float,
    /** What the wash settles to at the very bottom of the screen. */
    val floorAlpha: Float,
) {
    FULL(topAlpha = 0.30f, midAlpha = 0.10f, glowAlpha = 0.22f, floorAlpha = 0.06f),
    SUBTLE(topAlpha = 0.14f, midAlpha = 0.05f, glowAlpha = 0.10f, floorAlpha = 0.03f),
}

/**
 * A 0–1 value that drifts back and forth, or a fixed midpoint when motion is reduced.
 *
 * Checked through `ANIMATOR_DURATION_SCALE`, which is what "Remove animations" in Android's
 * accessibility settings actually sets, and what the platform's own animators consult.
 */
@Composable
private fun animatedDrift(): Float {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    if (!animationsEnabled) return DRIFT_MIDPOINT

    val transition = rememberInfiniteTransition(label = "weatherBackground")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear and slow. An eased or bouncing background would draw the eye, which is the
            // opposite of what a background is for.
            animation = tween(durationMillis = DRIFT_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "weatherBackgroundDrift",
    )
    return drift
}

/** Slow enough to read as changing light. */
private const val DRIFT_DURATION_MILLIS = 18_000
private const val DRIFT_MIDPOINT = 0.5f
private const val GLOW_X_MIN = 0.15f
private const val GLOW_X_TRAVEL = 0.7f
private const val GLOW_Y = 0.1f

/** Tighter than the full width, so the glow reads as a source of light. */
private const val GLOW_RADIUS_RATIO = 0.6f

/** The glow breathes between 70% and 130% of its base strength as it travels. */
private const val GLOW_ALPHA_MIN = 0.7f
private const val GLOW_ALPHA_TRAVEL = 0.6f
