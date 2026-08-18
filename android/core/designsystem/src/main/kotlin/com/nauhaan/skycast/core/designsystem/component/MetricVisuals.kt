package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
 * How a reading draws itself.
 *
 * Each case is a shape that suits a *kind* of reading rather than a specific metric, so the design
 * system decides how things look and the feature layer only says which kind it has.
 *
 * Mirrors `MetricVisual` on iOS.
 */
@Immutable
sealed interface MetricVisual {
    /** Nothing but the number, for readings with no scale worth drawing. */
    data object Plain : MetricVisual

    /**
     * A share of a known range, 0–1, on an arc.
     *
     * Reads as "how full" at a glance, which a bar also does, but an arc leaves the middle of the
     * tile free, so the number stays the largest thing in it.
     */
    data class Gauge(val fraction: Float) : MetricVisual

    /** A bearing in degrees, with its compass point already named. For wind. */
    data class Compass(val degrees: Float, val cardinal: String) : MetricVisual
}

/**
 * An arc showing where a reading sits on its own scale.
 *
 * 240° rather than a full circle, opening downwards: a closed ring reads as a progress spinner, and
 * the gap gives the eye a start and an end so "nearly full" is unambiguous.
 */
@Composable
fun MetricGauge(fraction: Float, colour: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        // Effects, not spatial: an arc that overshoots shows a value the reading never had.
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "metricGauge",
    )
    val track = weatherPalette.metricTrack

    Canvas(modifier = modifier.size(GaugeDiameter)) {
        val stroke = Stroke(width = GaugeStrokePx, cap = StrokeCap.Round)
        val inset = GaugeStrokePx / 2
        val arcSize = Size(size.width - GaugeStrokePx, size.height - GaugeStrokePx)
        drawArc(
            color = track,
            startAngle = GAUGE_START_DEGREES,
            sweepAngle = GAUGE_SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = colour,
            startAngle = GAUGE_START_DEGREES,
            sweepAngle = GAUGE_SWEEP_DEGREES * animated,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
}

/**
 * A compass rose with a needle on the bearing the wind is coming *from*.
 *
 * Meteorological convention: 270° is a westerly, blowing from the west. The needle points at the
 * source, which is what "westerly" means and what every aviation and marine chart shows, drawing it
 * as an arrow in the direction of travel would silently invert the reading for anyone who knows the
 * convention.
 */
@Composable
fun WindCompass(degrees: Float, colour: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = degrees,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "windCompass",
    )
    val ring = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RING_ALPHA)

    Canvas(
        modifier = modifier
            .size(GaugeDiameter)
            // Rotating the layer rather than the maths keeps the needle a straight line.
            .graphicsLayer { rotationZ = animated },
    ) {
        val radius = size.minDimension / 2
        val centre = Offset(size.width / 2, size.height / 2)
        drawCircle(color = ring, radius = radius - 1, style = Stroke(width = 1f))
        drawLine(
            color = colour,
            start = Offset(centre.x, centre.y - radius * NEEDLE_INNER),
            end = Offset(centre.x, centre.y - radius * NEEDLE_OUTER),
            strokeWidth = NeedleWidthPx,
            cap = StrokeCap.Round,
        )
    }
}

/** The sun-path card's content, already formatted by the feature layer. */
@Immutable
data class SunPathReading(
    val progress: Float,
    val sunriseLabel: String,
    val sunsetLabel: String,
    val daylightLabel: String,
    val contentDescription: String,
)

/**
 * The sun's day, as an arc from sunrise to sunset with a marker at now.
 *
 * This replaces two tiles that each showed a bare time. "05:50" and "20:18" are facts the reader has
 * to do arithmetic on before they mean anything; an arc with the sun three quarters along it answers
 * "how much daylight is left?" without any. The length of the day comes along as the caption, so a
 * third tile disappears too.
 */
@Composable
fun SunPathCard(reading: SunPathReading, modifier: Modifier = Modifier) {
    SkyPathCard(
        reading = SkyPathReading(
            progress = reading.progress,
            riseLabel = reading.sunriseLabel,
            setLabel = reading.sunsetLabel,
            centreLabel = reading.daylightLabel,
            contentDescription = reading.contentDescription,
        ),
        riseIcon = Icons.Filled.WbSunny,
        setIcon = Icons.Filled.WbTwilight,
        riseColour = weatherPalette.sunrise,
        setColour = weatherPalette.sunset,
        modifier = modifier,
    )
}

/** A rise-to-set card's content, already formatted by the feature layer. */
@Immutable
data class SkyPathReading(
    /** 0 at the rise, 1 at the set. Outside that range the body is below the horizon. */
    val progress: Float,
    val riseLabel: String,
    val setLabel: String,
    val centreLabel: String,
    val contentDescription: String,
)

/**
 * An arc from a rise to a set, with a marker at the body's current position.
 *
 * Generalised out of [SunPathCard] when the Moon tab needed the same drawing with different times,
 * colours and icons. Everything that makes it *readable*, the dashed remainder, the tinted
 * container, the single spoken sentence, is therefore defined once.
 */
@Composable
fun SkyPathCard(
    reading: SkyPathReading,
    riseIcon: ImageVector,
    setIcon: ImageVector,
    riseColour: Color,
    setColour: Color,
    modifier: Modifier = Modifier,
    markerColour: Color = riseColour,
) {
    val tint = LocalWeatherTint.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val container = if (tint == null) base else tint.copy(alpha = TINT_ALPHA).compositeOver(base)
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA)
    val progress = reading.progress.coerceIn(0f, 1f)
    val isUp = reading.progress > 0f && reading.progress < 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = reading.contentDescription },
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ArcHeight),
            ) {
                val path = sunArc(size)
                drawPath(
                    path = path,
                    color = track,
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                    ),
                )
                // The travelled part is drawn as its own shorter arc rather than by trimming the
                // path: Compose has no `trim`, and re-deriving the curve for a partial sweep is
                // exact where clipping the full one would flatten its end.
                drawPath(
                    path = sunArc(size, until = progress),
                    brush = Brush.horizontalGradient(listOf(riseColour, setColour)),
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
                if (isUp) {
                    drawCircle(
                        color = markerColour,
                        radius = MarkerRadiusPx,
                        center = sunArcPoint(progress, size),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeLabel(reading.riseLabel, riseIcon, riseColour)
                Text(
                    text = reading.centreLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeLabel(reading.setLabel, setIcon, setColour)
            }
        }
    }
}

/** Rise and set icons for a body with no dawn or dusk of its own. */
val MoonRiseIcon: ImageVector get() = Icons.Filled.ArrowUpward

/** @see MoonRiseIcon */
val MoonSetIcon: ImageVector get() = Icons.Filled.ArrowDownward

@Composable
private fun TimeLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(LabelIconSize),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/**
 * A shallow quadratic arc across the box, flat enough to read as a horizon rather than a rainbow.
 *
 * [until] draws only the first part of it, by evaluating the curve rather than clipping.
 */
private fun sunArc(size: Size, until: Float = 1f): Path = Path().apply {
    val start = sunArcPoint(0f, size)
    moveTo(start.x, start.y)
    val fraction = until.coerceIn(0f, 1f)
    val steps = (SUN_ARC_STEPS * fraction).toInt().coerceAtLeast(1)
    for (step in 1..steps) {
        // `step / steps`, not `step / SUN_ARC_STEPS`. The latter scales by the fraction twice, so
        // the drawn arc stopped at progress², visibly short of the sun marker, which is evaluated
        // from the same curve at the real progress. Caught by looking at it.
        val point = sunArcPoint(fraction * step / steps, size)
        lineTo(point.x, point.y)
    }
}

/** The point on that curve at [progress], for placing the marker and for drawing partial arcs. */
private fun sunArcPoint(progress: Float, size: Size): Offset {
    val t = progress.coerceIn(0f, 1f)
    val inverse = 1 - t
    val controlY = -size.height * SUN_ARC_LIFT
    return Offset(
        x = 2 * inverse * t * (size.width / 2) + t * t * size.width,
        y = inverse * inverse * size.height + 2 * inverse * t * controlY + t * t * size.height,
    )
}

private val GaugeDiameter = 44.dp
private val ArcHeight = 64.dp
private val LabelIconSize = 16.dp
private const val GaugeStrokePx = 12f
private const val NeedleWidthPx = 7f
private const val MarkerRadiusPx = 12f

/** 150° puts the gauge's opening at the bottom, symmetrical about vertical. */
private const val GAUGE_START_DEGREES = 150f
private const val GAUGE_SWEEP_DEGREES = 240f
private const val NEEDLE_INNER = 0.1f
private const val NEEDLE_OUTER = 0.82f
private const val RING_ALPHA = 0.2f
private const val TRACK_ALPHA = 0.25f
private const val TINT_ALPHA = 0.45f

/** Lifts the control point above the box so the drawn curve peaks inside it. */
private const val SUN_ARC_LIFT = 0.6f

/** Enough segments that the polyline reads as a curve at this size. */
private const val SUN_ARC_STEPS = 48

@Preview(showBackground = true)
@Composable
private fun MetricVisualsPreview() {
    SkyCastTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MetricGauge(fraction = 0.78f, colour = weatherPalette.humidity)
                MetricGauge(fraction = 0.2f, colour = weatherPalette.pressure)
                WindCompass(degrees = 270f, colour = weatherPalette.wind)
            }
            SunPathCard(
                reading = SunPathReading(
                    progress = 0.62f,
                    sunriseLabel = "05:50",
                    sunsetLabel = "20:18",
                    daylightLabel = "14h 28m",
                    contentDescription = "Sunrise 05:50, sunset 20:18",
                ),
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}
