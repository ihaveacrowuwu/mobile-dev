package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.LocalWeatherTint
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherPalette

/**
 * The secondary readings, humidity, wind, pressure, visibility, sunrise, sunset.
 *
 * A `FlowRow` rather than a fixed grid so the tiles reflow instead of clipping at large font
 * sizes, which is what the Dynamic Type requirement actually demands.
 *
 * Each tile carries its own colour and, where the reading has a scale, an indicator showing where
 * on that scale it sits. "1014 hPa" tells most people nothing; a bar sitting just past the middle
 * tells them it is an ordinary day. The colours come from the weather palette, so humidity is the
 * same blue everywhere it appears.
 *
 * Each tile announces as one unit; without that, TalkBack reads a label and a bare number as two
 * unrelated fragments.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherDetailGrid(details: List<WeatherDetail>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        maxItemsInEachRow = TILES_PER_ROW,
    ) {
        details.forEach { detail ->
            WeatherDetailTile(
                detail = detail,
                // weight(1f) inside a FlowRow gives equal-width tiles that still reflow: at large
                // font sizes a row simply holds fewer of them.
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeatherDetailTile(detail: WeatherDetail, modifier: Modifier = Modifier) {
    val accent = detail.kind.accent()

    // The theme surface with a whisper of the page's mood mixed over it. Composited rather than
    // replaced so the base stays the colour the theme guarantees contrast against: the tile belongs
    // to a warm page or a cold one without any text on it becoming a contrast problem that has to
    // be re-checked per condition. Animated on the effects spec, this is a colour change, and the
    // spatial spec would overshoot past the target colour.
    val tint = LocalWeatherTint.current
    val container by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerHigh.let { surface ->
            if (tint == null) surface else tint.copy(alpha = TINT_ALPHA).compositeOver(surface)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "detailTileContainer",
    )

    Card(
        modifier = modifier.clearAndSetSemantics {
            // The compass draws a bearing the value never mentions, so it is spoken.
            val bearing = (detail.visual as? MetricVisual.Compass)?.let { " from the ${it.cardinal}" } ?: ""
            contentDescription = "${detail.label}, ${detail.value}$bearing"
        },
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = detail.kind.icon(),
                    // Decorative: the label beside it says the same thing in words.
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(IconSize),
                )
                Text(
                    text = detail.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = detail.value,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                when (val visual = detail.visual) {
                    MetricVisual.Plain -> Unit
                    is MetricVisual.Gauge -> MetricGauge(fraction = visual.fraction, colour = accent)
                    is MetricVisual.Compass -> WindCompass(degrees = visual.degrees, colour = accent)
                }
            }
        }
    }
}

private fun WeatherDetailKind.icon(): ImageVector = when (this) {
    WeatherDetailKind.HUMIDITY -> Icons.Filled.WaterDrop
    WeatherDetailKind.WIND -> Icons.Filled.Air
    WeatherDetailKind.PRESSURE -> Icons.Filled.Compress
    WeatherDetailKind.VISIBILITY -> Icons.Filled.Visibility
    WeatherDetailKind.SUNRISE -> Icons.Filled.WbSunny
    WeatherDetailKind.SUNSET -> Icons.Filled.WbTwilight
    WeatherDetailKind.CLOUD -> Icons.Filled.Cloud
    WeatherDetailKind.DEW_POINT -> Icons.Filled.DeviceThermostat
    WeatherDetailKind.DAYLIGHT -> Icons.Filled.LightMode
}

@Composable
private fun WeatherDetailKind.accent(): Color = when (this) {
    WeatherDetailKind.HUMIDITY -> weatherPalette.humidity
    WeatherDetailKind.WIND -> weatherPalette.wind
    WeatherDetailKind.PRESSURE -> weatherPalette.pressure
    WeatherDetailKind.VISIBILITY -> weatherPalette.visibility
    WeatherDetailKind.SUNRISE -> weatherPalette.sunrise
    WeatherDetailKind.SUNSET -> weatherPalette.sunset
    WeatherDetailKind.CLOUD -> weatherPalette.onCloudContainer
    WeatherDetailKind.DEW_POINT -> weatherPalette.humidity
    WeatherDetailKind.DAYLIGHT -> weatherPalette.sunrise
}

/** Two tiles per row at the default text size; FlowRow drops to one when they stop fitting. */
private const val TILES_PER_ROW = 2

/**
 * Enough to be felt when swiping between a clear place and an overcast one, little enough that the
 * tile still reads as a surface rather than a coloured chip.
 *
 * Higher than it was, and lighter for it: 10% of the *mood* hue put a mid-dark slate over a light
 * card and the result read as dirty grey. 45% of the container keeps a light card light.
 */
private const val TINT_ALPHA = 0.45f
private val IconSize = 16.dp

@Preview(showBackground = true)
@Composable
private fun WeatherDetailGridPreview() {
    SkyCastTheme {
        WeatherDetailGrid(
            details = listOf(
                WeatherDetail("Humidity", "69%", WeatherDetailKind.HUMIDITY, MetricVisual.Gauge(0.69f)),
                WeatherDetail(
                    "Wind",
                    "4.5 m/s",
                    WeatherDetailKind.WIND,
                    MetricVisual.Compass(degrees = 270f, cardinal = "W"),
                ),
                WeatherDetail("Pressure", "1009 hPa", WeatherDetailKind.PRESSURE, MetricVisual.Gauge(0.45f)),
                WeatherDetail("Visibility", "10.0 km", WeatherDetailKind.VISIBILITY, MetricVisual.Gauge(1f)),
                WeatherDetail("Cloud cover", "40%", WeatherDetailKind.CLOUD, MetricVisual.Gauge(0.4f)),
                WeatherDetail("Dew point", "12°C", WeatherDetailKind.DEW_POINT, MetricVisual.Gauge(0.6f)),
            ),
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
