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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing

/**
 * Whether the aurora is worth going outside for.
 *
 * The bar shows *how far off* it is: a green band this place needs the disturbance to reach, and a
 * marker for how far tonight's forecast gets.
 *
 * The Swift twin is `AuroraCard.swift`.
 */
@Composable
fun AuroraCard(reading: AuroraReading, modifier: Modifier = Modifier) {
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
            Text(text = reading.headline, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                text = reading.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Canvas(modifier = Modifier.fillMaxWidth().height(BarHeight)) {
                // The scale itself: quiet on the left, storm on the right.
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Quiet, Active, Storm)),
                    cornerRadius = CornerRadius(size.height / 2),
                    alpha = SCALE_ALPHA,
                )
                // Where this place starts seeing anything, the threshold the reader is waiting for.
                val threshold = size.width * reading.reachFraction.coerceIn(0f, 1f)
                drawLine(
                    color = Aurora,
                    start = Offset(threshold, 0f),
                    end = Offset(threshold, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
                // And how far tonight gets.
                val peak = (size.width * reading.peakFraction.coerceIn(0f, 1f))
                    .coerceIn(MarkerRadiusPx, size.width - MarkerRadiusPx)
                drawCircle(color = Color.White, radius = MarkerRadiusPx, center = Offset(peak, size.height / 2))
                drawCircle(
                    color = Storm,
                    radius = MarkerRadiusPx,
                    center = Offset(peak, size.height / 2),
                    style = Stroke(width = 2f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reading.kpNowLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = reading.kpPeakLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The aurora card's content, already formatted by the feature layer. */
@Immutable
data class AuroraReading(
    /** "Not tonight", "Possible late on", and so on. */
    val headline: String,
    /** The one line that says what to do about it. */
    val detail: String,
    val kpNowLabel: String,
    val kpPeakLabel: String,
    /** Where this place sits across the Kp scale it would need, 0–1. */
    val reachFraction: Float,
    /** Where tonight's forecast peak sits on the same scale, 0–1. */
    val peakFraction: Float,
    val contentDescription: String,
)

// The aurora's own colours, not the theme's: a green curtain over a quiet-to-stormy scale.
private val Quiet = Color(0xFF3A4A6B)
private val Active = Color(0xFF6B5AA8)
private val Storm = Color(0xFFC0507A)
private val Aurora = Color(0xFF5BE0A0)

private val BarHeight = 16.dp
private const val MarkerRadiusPx = 7f
private const val SCALE_ALPHA = 0.75f

@Preview(showBackground = true)
@Composable
private fun AuroraCardPreview() {
    SkyCastTheme {
        AuroraCard(
            reading = AuroraReading(
                headline = "Not tonight",
                detail = "London needs Kp 6 before the aurora reaches this far south. Tonight peaks at 4.7.",
                kpNowLabel = "Now Kp 5.0 · G1",
                kpPeakLabel = "Tonight up to Kp 4.7",
                reachFraction = 6 / 9f,
                peakFraction = 4.67f / 9f,
                contentDescription = "Aurora not expected tonight. London needs Kp 6; tonight peaks at 4.7.",
            ),
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
