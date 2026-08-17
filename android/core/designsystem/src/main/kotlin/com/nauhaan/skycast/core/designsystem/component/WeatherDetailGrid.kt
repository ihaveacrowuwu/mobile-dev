package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing

/**
 * The secondary readings, humidity, wind, pressure, visibility, sunrise, sunset.
 *
 * A `FlowRow` rather than a fixed grid so the tiles reflow instead of clipping at large font
 * sizes, which is what the Dynamic Type requirement actually demands.
 *
 * Each tile announces as one unit; without that, TalkBack reads a label and a bare number as
 * two unrelated fragments.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherDetailGrid(details: List<WeatherDetail>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        details.forEach { detail ->
            Card {
                Column(
                    modifier = Modifier
                        .padding(Spacing.md)
                        .clearAndSetSemantics {
                            contentDescription = "${detail.label}, ${detail.value}"
                        },
                ) {
                    Text(
                        text = detail.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = detail.value,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun WeatherDetailGridPreview() {
    SkyCastTheme {
        WeatherDetailGrid(
            details = listOf(
                WeatherDetail("Humidity", "69%"),
                WeatherDetail("Wind", "4.5 m/s"),
                WeatherDetail("Pressure", "1009 hPa"),
                WeatherDetail("Visibility", "10.0 km"),
                WeatherDetail("Sunrise", "05:27"),
                WeatherDetail("Sunset", "20:46"),
            ),
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
