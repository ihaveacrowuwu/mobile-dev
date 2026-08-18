package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing

/**
 * The evening's light, as a band that runs from gold to blue to night.
 *
 * Computed from the sun's altitude rather than read from a weather API, so its length depends on
 * where you are. See `SolarCalculator`.
 *
 * The Swift twin is `GoldenHourCard.swift`.
 */
@Composable
fun GoldenHourCard(reading: GoldenHourReading, modifier: Modifier = Modifier) {
    val shape = CardDefaults.shape

    Card(
        modifier = modifier
            .fillMaxWidth()
            .frostRim(shape)
            .clearAndSetSemantics { contentDescription = reading.contentDescription },
        shape = shape,
        colors = frostedCardColours(),
        elevation = frostedCardElevation(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LightLabel(reading.goldenLabel, reading.goldenRangeLabel, Golden)
                LightLabel(reading.blueLabel, reading.blueRangeLabel, Blue, alignEnd = true)
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(BandHeight)) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Golden, Amber, Blue, Night)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
                reading.progress?.let { progress ->
                    // A marker for now, so the band answers "how long have I got?" rather than only "when".
                    val x = (size.width * progress).coerceIn(MarkerRadiusPx, size.width - MarkerRadiusPx)
                    drawCircle(
                        color = Color.White,
                        radius = MarkerRadiusPx,
                        center = Offset(x, size.height / 2),
                    )
                    drawCircle(
                        color = Night.copy(alpha = MARKER_RING_ALPHA),
                        radius = MarkerRadiusPx,
                        center = Offset(x, size.height / 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

/** The golden-hour card's content, already formatted by the feature layer. */
@Immutable
data class GoldenHourReading(
    val goldenLabel: String,
    val blueLabel: String,
    val goldenRangeLabel: String,
    val blueRangeLabel: String,
    /** Where now sits across the whole golden-plus-blue span, 0–1, or `null` when it has not started. */
    val progress: Float?,
    val contentDescription: String,
)

@Composable
private fun LightLabel(title: String, range: String, colour: Color, alignEnd: Boolean = false) {
    Column(
        horizontalAlignment = if (alignEnd) {
            androidx.compose.ui.Alignment.End
        } else {
            androidx.compose.ui.Alignment.Start
        },
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = colour)
        Text(
            text = range,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// The light itself, not theme colours: this band is a picture of the sky at dusk, and the sky does not take
// its colours from a wallpaper.
private val Golden = Color(0xFFF5B759)
private val Amber = Color(0xFFE07A4B)
private val Blue = Color(0xFF4A6DA8)
private val Night = Color(0xFF1B2340)

private val BandHeight = 18.dp
private const val MarkerRadiusPx = 7f
private const val MARKER_RING_ALPHA = 0.55f

@Preview(showBackground = true)
@Composable
private fun GoldenHourCardPreview() {
    SkyCastTheme {
        GoldenHourCard(
            reading = GoldenHourReading(
                goldenLabel = "Golden hour",
                blueLabel = "Blue hour",
                goldenRangeLabel = "19:29 – 20:37",
                blueRangeLabel = "20:37 – 20:51",
                progress = 0.4f,
                contentDescription = "Golden hour from 19:29 to 20:37, then blue hour until 20:51",
            ),
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
