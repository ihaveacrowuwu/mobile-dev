package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

/**
 * The sky above the field, drawn to scale, with the coded values beside it.
 *
 * The vertical scale is real: a layer at 4800 ft sits roughly twice as high as one at 2400 ft, so
 * two observations can be compared at a glance. The scale is taken from the highest layer rather
 * than fixed, so a 1200 ft overcast day does not draw as a band along the floor.
 *
 * The Swift twin is `SkyLayersDiagram.swift`.
 */
@Composable
fun SkyLayersDiagram(
    layers: List<SkyLayer>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cloudColour: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ceilingColour: Color = MaterialTheme.colorScheme.primary,
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = cloudColour)
    val groundColour = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DiagramHeight)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(DiagramHeight).padding(Spacing.sm)) {
            val topFeet = scaleTopFeet(layers)

            drawAxis(topFeet, measurer, axisStyle, groundColour)
            for (layer in layers) {
                drawLayer(layer, topFeet, cloudColour, ceilingColour, measurer, axisStyle)
            }
            drawGround(groundColour)
        }
    }
}

/**
 * One cloud layer, ready to draw.
 *
 * A presentation type rather than the domain's `CloudLayer`: the diagram needs a *fraction* of sky covered,
 * and turning "BKN" into three quarters is a decision about how to draw the word, not about what the word
 * means.
 */
@Immutable
data class SkyLayer(
    /** The coverage abbreviation as issued, FEW, SCT, BKN, OVC. */
    val cover: String,
    val baseFeet: Int,
    /** How much of the width this layer's band fills, 0–1. */
    val coverFraction: Float,
    /** Broken and overcast layers form the ceiling; the lowest of them is marked. */
    val isCeiling: Boolean,
)

/**
 * The height the top of the diagram represents.
 *
 * Rounded up to a round number above the highest layer so the axis labels are readable, and floored at
 * [MINIMUM_TOP_FEET] so a clear sky is not an empty box with no sense of scale.
 */
private fun scaleTopFeet(layers: List<SkyLayer>): Int {
    val highest = layers.maxOfOrNull { it.baseFeet } ?: 0
    val padded = (highest * SCALE_HEADROOM).roundToInt()
    val rounded = ((padded + AXIS_STEP_FEET - 1) / AXIS_STEP_FEET) * AXIS_STEP_FEET
    return maxOf(rounded, MINIMUM_TOP_FEET)
}

/** Height labels up the right-hand edge, so the vertical positions mean something. */
private fun DrawScope.drawAxis(topFeet: Int, measurer: TextMeasurer, style: TextStyle, colour: Color) {
    var feet = AXIS_STEP_FEET
    while (feet <= topFeet) {
        val y = yFor(feet, topFeet, size)
        drawLine(
            color = colour.copy(alpha = AXIS_ALPHA),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        val label = measurer.measure("${feet / 1000}k", style)
        drawText(
            textLayoutResult = label,
            // Clamped into the box. The topmost gridline sits at the very top, and a label placed a
            // label-height above it was drawn off the edge of the card, visible immediately as a
            // half-cut "6k" against the border.
            topLeft = Offset(
                size.width - label.size.width,
                (y - label.size.height).coerceAtLeast(0f),
            ),
        )
        feet += AXIS_STEP_FEET
    }
}

/**
 * One layer, as a band of cloud puffs at its height.
 *
 * The band's width carries the coverage, a FEW layer is a quarter of the sky, an OVC layer all of it, which
 * is the same information the abbreviation carries, in a form that needs no glossary.
 */
private fun DrawScope.drawLayer(
    layer: SkyLayer,
    topFeet: Int,
    cloudColour: Color,
    ceilingColour: Color,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val y = yFor(layer.baseFeet, topFeet, size)
    val bandWidth = size.width * CLOUD_AREA_FRACTION * layer.coverFraction
    val puffs = (layer.coverFraction * MAX_PUFFS).roundToInt().coerceAtLeast(1)
    val puffRadius = (bandWidth / puffs / 2).coerceAtMost(MaxPuffRadiusPx)

    for (index in 0 until puffs) {
        val centreX = puffRadius + index * (bandWidth / puffs)
        drawCircle(
            color = cloudColour.copy(alpha = CLOUD_ALPHA),
            radius = puffRadius,
            center = Offset(centreX, y),
        )
    }

    if (layer.isCeiling) {
        // The ceiling gets a dashed rule and a label, because it is the one height that decides whether a
        // flight can be made under visual rules at all.
        drawLine(
            color = ceilingColour,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        )
        val label = measurer.measure("${layer.cover} ${layer.baseFeet} ft", style.copy(color = ceilingColour))
        drawText(
            textLayoutResult = label,
            // Above the puffs, not just above the line: the band is centred on the line and reaches a
            // puff radius either side of it, so a label placed one label-height up landed inside the
            // cloud and was unreadable.
            topLeft = Offset(
                0f,
                (y - puffRadius - label.size.height - CeilingLabelGapPx).coerceAtLeast(0f),
            ),
        )
    }
}

/** The ground, so the heights are heights *above the field* rather than above nothing. */
private fun DrawScope.drawGround(colour: Color) {
    drawLine(
        color = colour,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 3f,
    )
}

private fun yFor(feet: Int, topFeet: Int, size: Size): Float =
    size.height * (1f - (feet.toFloat() / topFeet)).coerceIn(0f, 1f)

/** How much of a coverage abbreviation's sky is filled, for [SkyLayer.coverFraction]. */
fun coverFractionFor(cover: String): Float = when (cover.uppercase()) {
    "FEW" -> 0.25f
    "SCT" -> 0.5f
    "BKN" -> 0.75f
    "OVC", "VV" -> 1f
    else -> 0.15f
}

private val DiagramHeight = 200.dp
private const val MINIMUM_TOP_FEET = 5_000
private const val AXIS_STEP_FEET = 2_000
private const val SCALE_HEADROOM = 1.25
private const val CLOUD_AREA_FRACTION = 0.72f
private const val MAX_PUFFS = 7
private const val CLOUD_ALPHA = 0.55f
private const val AXIS_ALPHA = 0.15f
private const val MaxPuffRadiusPx = 22f

/** Clear air between the top of a cloud band and its label. */
private const val CeilingLabelGapPx = 6f

@Preview(showBackground = true)
@Composable
private fun SkyLayersDiagramPreview() {
    SkyCastTheme {
        SkyLayersDiagram(
            layers = listOf(
                SkyLayer("FEW", 1_200, 0.25f, isCeiling = false),
                SkyLayer("SCT", 2_500, 0.5f, isCeiling = false),
                SkyLayer("BKN", 4_800, 0.75f, isCeiling = true),
                SkyLayer("OVC", 7_000, 1f, isCeiling = false),
            ),
            contentDescription = "Ceiling broken at 4800 feet",
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
