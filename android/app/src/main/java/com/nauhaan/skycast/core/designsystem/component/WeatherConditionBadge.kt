package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.WeatherCondition

/**
 * The current condition, as an icon inside a **Material 3 Expressive shape**.
 *
 * Expressive's `MaterialShapes` are a set of `RoundedPolygon`s intended to give a surface
 * character rather than defaulting every container to a rounded rectangle. They fit a
 * weather app unusually well: the shape itself can carry meaning, so clear skies get
 * `Sunny`, a thunderstorm gets the jagged `Gem`, and drizzle gets a soft `Oval`.
 *
 * The colour transition uses the Expressive motion scheme
 * (`MaterialTheme.motionScheme.defaultEffectsSpec()`) so this animates in step with the
 * built-in components instead of at some arbitrary duration of its own.
 */
@Composable
fun WeatherConditionBadge(
    condition: WeatherCondition,
    isDaytime: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = BadgeSize,
) {
    val containerColour by animateColorAsState(
        targetValue = condition.containerColour(isDaytime),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "conditionBadgeContainer",
    )

    Box(
        modifier = modifier
            .size(size)
            // toShape() converts the RoundedPolygon into a Compose Shape.
            .clip(condition.expressiveShape().toShape())
            .background(containerColour),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = condition.icon(isDaytime),
            // Decorative: the surrounding text already names the condition, so
            // announcing the icon too would repeat it for screen-reader users.
            contentDescription = null,
            tint = condition.contentColour(isDaytime),
            modifier = Modifier.size(size / 2),
        )
    }
}

/**
 * Maps a condition to an Expressive shape.
 *
 * Chosen so the silhouette reinforces the reading: spiky for storms, soft for drizzle,
 * scalloped for cloud.
 */
private fun WeatherCondition.expressiveShape(): RoundedPolygon = when (this) {
    WeatherCondition.CLEAR -> MaterialShapes.Sunny
    WeatherCondition.CLOUDS -> MaterialShapes.Cookie9Sided
    WeatherCondition.RAIN -> MaterialShapes.Pill
    WeatherCondition.DRIZZLE -> MaterialShapes.Oval
    WeatherCondition.THUNDERSTORM -> MaterialShapes.Gem
    WeatherCondition.SNOW -> MaterialShapes.VerySunny
    WeatherCondition.MIST -> MaterialShapes.ClamShell
    WeatherCondition.UNKNOWN -> MaterialShapes.Circle
}

/**
 * Icon for a condition, varying by time of day where it matters.
 *
 * [isDaytime] is not decoration: a sun icon at 4am is simply wrong. Clear and cloudy skies are
 * the two conditions where night genuinely changes the symbol; rain and snow look the same at any
 * hour.
 *
 * Kept in step with `WeatherCondition.symbolName(isDaytime:)` on iOS.
 */
private fun WeatherCondition.icon(isDaytime: Boolean): ImageVector = when (this) {
    WeatherCondition.CLEAR -> if (isDaytime) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    WeatherCondition.CLOUDS -> if (isDaytime) Icons.Filled.Cloud else Icons.Filled.NightsStay
    WeatherCondition.RAIN -> Icons.Filled.Umbrella
    WeatherCondition.DRIZZLE -> Icons.Filled.WaterDrop
    WeatherCondition.THUNDERSTORM -> Icons.Filled.Thunderstorm
    WeatherCondition.SNOW -> Icons.Filled.AcUnit
    WeatherCondition.MIST -> Icons.Filled.Grain
    WeatherCondition.UNKNOWN -> Icons.Filled.QuestionMark
}

@Composable
private fun WeatherCondition.containerColour(isDaytime: Boolean): Color = when (this) {
    // Clear skies read warm by day and cool by night, the one condition where time of
    // day genuinely changes the impression.
    WeatherCondition.CLEAR ->
        if (isDaytime) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }

    WeatherCondition.THUNDERSTORM -> MaterialTheme.colorScheme.errorContainer
    WeatherCondition.RAIN, WeatherCondition.DRIZZLE -> MaterialTheme.colorScheme.primaryContainer
    WeatherCondition.SNOW, WeatherCondition.MIST, WeatherCondition.CLOUDS ->
        MaterialTheme.colorScheme.secondaryContainer

    WeatherCondition.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun WeatherCondition.contentColour(isDaytime: Boolean): Color = when (this) {
    WeatherCondition.CLEAR ->
        if (isDaytime) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

    WeatherCondition.THUNDERSTORM -> MaterialTheme.colorScheme.onErrorContainer
    WeatherCondition.RAIN, WeatherCondition.DRIZZLE -> MaterialTheme.colorScheme.onPrimaryContainer
    WeatherCondition.SNOW, WeatherCondition.MIST, WeatherCondition.CLOUDS ->
        MaterialTheme.colorScheme.onSecondaryContainer

    WeatherCondition.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val BadgeSize: Dp = 96.dp

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Condition badges, every shape", showBackground = true)
@Composable
private fun WeatherConditionBadgePreview() {
    SkyCastTheme {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.md),
        ) {
            WeatherCondition.entries.forEach { condition ->
                WeatherConditionBadge(condition = condition, isDaytime = true, size = 72.dp)
            }
            WeatherCondition.entries.forEach { condition ->
                WeatherConditionBadge(condition = condition, isDaytime = false, size = 72.dp)
            }
        }
    }
}
