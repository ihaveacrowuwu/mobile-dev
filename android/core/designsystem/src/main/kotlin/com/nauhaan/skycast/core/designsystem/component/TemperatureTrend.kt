package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherPalette

/**
 * Temperature over the forecast period, as a filled line.
 *
 * A `Canvas` path with a gradient beneath it, drawn from the app's own palette. The counterpart on
 * iOS uses Swift Charts, which ships with the OS.
 *
 * The vertical scale is padded a little beyond the real range, so the warmest point does not sit on
 * the top edge with its label clipped. Only the extremes are annotated.
 *
 * The whole thing is a single accessibility element with a spoken summary, so a screen-reader user
 * gets "18° to 28°, rising to a high on Thursday".
 */
@Composable
fun TemperatureTrend(
    points: List<TrendPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = TrendHeight,
) {
    if (points.size < MINIMUM_POINTS) return

    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val marker = weatherPalette.sunset
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = axis)
    val markerStyle = MaterialTheme.typography.labelMedium.copy(color = marker)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        drawTrend(
            points = points,
            line = line,
            fill = fill,
            marker = marker,
            axis = axis,
            measurer = measurer,
            labelStyle = labelStyle,
            markerStyle = markerStyle,
        )
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawTrend(
    points: List<TrendPoint>,
    line: Color,
    fill: Color,
    marker: Color,
    axis: Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    markerStyle: TextStyle,
) {
    val values = points.map { it.value }
    val lowest = values.min()
    val highest = values.max()
    // A flat forecast would otherwise divide by zero and draw a line off the top of the box.
    val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
    val padding = span * RANGE_PADDING
    val floor = lowest - padding
    val ceiling = highest + padding

    val plotHeight = size.height - AXIS_LABEL_HEIGHT_PX
    val step = size.width / (points.size - 1)

    fun pointAt(index: Int): Offset {
        val fraction = (values[index] - floor) / (ceiling - floor)
        return Offset(x = index * step, y = (plotHeight * (1 - fraction)).toFloat())
    }

    val path = Path().apply {
        moveTo(pointAt(0).x, pointAt(0).y)
        for (index in 1 until points.size) {
            val point = pointAt(index)
            lineTo(point.x, point.y)
        }
    }

    // The fill is the same path closed down to the baseline, so it can never disagree with the
    // line above it.
    val area = Path().apply {
        addPath(path)
        lineTo(size.width, plotHeight)
        lineTo(0f, plotHeight)
        close()
    }
    drawPath(
        path = area,
        brush = Brush.verticalGradient(
            colors = listOf(fill.copy(alpha = FILL_TOP_ALPHA), Color.Transparent),
            endY = plotHeight,
        ),
    )
    drawPath(path = path, color = line, style = Stroke(width = LineWidthPx))

    // Day boundaries: a hairline and a label, so the shape can be read against the calendar.
    points.forEachIndexed { index, point ->
        val label = point.dayLabel ?: return@forEachIndexed
        val x = index * step
        drawLine(
            color = axis.copy(alpha = GRIDLINE_ALPHA),
            start = Offset(x, 0f),
            end = Offset(x, plotHeight),
            strokeWidth = GridlineWidthPx,
        )
        val measured = measurer.measure(label, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                // Nudged inside the box at both ends so neither the first nor the last day
                // label is half cut off.
                x = (x + LABEL_GAP_PX).coerceAtMost(size.width - measured.size.width),
                y = plotHeight + LABEL_GAP_PX,
            ),
        )
    }

    // Only the extremes are annotated.
    listOf(values.indexOf(highest), values.indexOf(lowest)).distinct().forEach { index ->
        val point = pointAt(index)
        drawCircle(color = marker, radius = MarkerRadiusPx, center = point)
        val measured = measurer.measure(points[index].valueLabel, markerStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = (point.x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                y = (point.y - measured.size.height - LABEL_GAP_PX).coerceAtLeast(0f),
            ),
        )
    }
}

/** Tall enough for the shape to be readable, short enough to leave room for the day rows below. */
private val TrendHeight = 132.dp
private const val MINIMUM_POINTS = 2

/** Headroom above and below the real range, so extreme labels are not clipped by the box. */
private const val RANGE_PADDING = 0.18
private const val FILL_TOP_ALPHA = 0.28f
private const val GRIDLINE_ALPHA = 0.25f
private const val AXIS_LABEL_HEIGHT_PX = 34f
private const val LABEL_GAP_PX = 4f
private const val LineWidthPx = 4f
private const val GridlineWidthPx = 1f
private const val MarkerRadiusPx = 5f

@Preview(showBackground = true)
@Composable
private fun TemperatureTrendPreview() {
    val temperatures = listOf(18.0, 17.0, 19.0, 24.0, 27.0, 25.0, 21.0, 19.0, 18.0, 20.0, 26.0, 28.0)
    SkyCastTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("Next five days", style = MaterialTheme.typography.titleMedium)
            TemperatureTrend(
                points = temperatures.mapIndexed { index, value ->
                    TrendPoint(
                        value = value,
                        valueLabel = "${value.toInt()}°",
                        dayLabel = "Day ${index / 4 + 1}".takeIf { index % 4 == 0 },
                    )
                },
                contentDescription = "Temperature from 17 to 28 degrees over five days",
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
