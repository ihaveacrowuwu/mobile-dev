package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherPalette
import com.nauhaan.skycast.domain.model.WeatherCondition

/**
 * The forecast's days, each as a labelled range bar.
 *
 * @param onDaySelected makes each row a button through to that day's hour-by-hour breakdown. `null`
 *   leaves the rows inert, which is what a preview or a summary wants.
 */
@Composable
fun DailyRangeList(days: List<DayRange>, modifier: Modifier = Modifier, onDaySelected: ((DayRange) -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        days.forEach { day ->
            DailyRangeRow(
                day = day,
                modifier = if (onDaySelected == null) {
                    Modifier
                } else {
                    Modifier
                        // A row, not the bar: the whole row is the target, which clears the 48 dp
                        // minimum comfortably where a 6 dp bar never could.
                        .clickable(onClickLabel = day.openLabel) { onDaySelected(day) }
                        .padding(vertical = Spacing.xs)
                },
            )
        }
    }
}

@Composable
private fun DailyRangeRow(day: DayRange, modifier: Modifier = Modifier) {
    val cold = weatherPalette.humidity
    val warm = weatherPalette.sunset
    val track = weatherPalette.metricTrack

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = day.contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.width(DayColumnWidth)) {
            Text(text = day.dayLabel, style = MaterialTheme.typography.bodyMedium)
            if (day.precipitationLabel != null) {
                Text(
                    text = day.precipitationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = weatherPalette.humidity,
                )
            }
        }

        WeatherConditionBadge(
            condition = day.condition,
            isDaytime = day.isDaytime,
            size = BadgeSize,
        )

        Text(
            text = day.lowLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(TemperatureColumnWidth),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(BarHeight)
                .clip(MaterialTheme.shapes.extraSmall)
                // Drawn rather than laid out, for the same reason as the metric bar: a child sized
                // to a fraction of `maxWidth` overflows when a parent measures intrinsics.
                .drawBehind {
                    drawRect(color = track)
                    val start = size.width * day.lowFraction
                    val end = size.width * day.highFraction
                    drawRect(
                        // Cool at the day's low end, warm at its high end, the same reading the
                        // two numbers either side give, in a form the eye takes in at a glance.
                        brush = Brush.horizontalGradient(
                            colors = listOf(cold, warm),
                            startX = start,
                            endX = end.coerceAtLeast(start + 1f),
                        ),
                        topLeft = Offset(start, 0f),
                        size = Size((end - start).coerceAtLeast(MinimumBarWidthPx), size.height),
                    )
                },
        )

        Text(
            text = day.highLabel,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(TemperatureColumnWidth),
        )
    }
}

private val DayColumnWidth = 76.dp
private val TemperatureColumnWidth = 40.dp
private val BadgeSize = 28.dp
private val BarHeight = 6.dp

/** A day whose high equals its low still needs to be visible. */
private const val MinimumBarWidthPx = 6f

@Preview(showBackground = true)
@Composable
private fun DailyRangeListPreview() {
    SkyCastTheme {
        DailyRangeList(
            days = listOf(
                DayRange(
                    dayLabel = "Today",
                    condition = WeatherCondition.CLEAR,
                    isDaytime = true,
                    lowLabel = "19°",
                    highLabel = "28°",
                    lowFraction = 0.2f,
                    highFraction = 0.9f,
                    precipitationLabel = "20%",
                    contentDescription = "Today, clear sky, low 19 degrees, high 28 degrees",
                ),
                DayRange(
                    dayLabel = "Wednesday",
                    condition = WeatherCondition.RAIN,
                    isDaytime = true,
                    lowLabel = "17°",
                    highLabel = "24°",
                    lowFraction = 0.0f,
                    highFraction = 0.6f,
                    precipitationLabel = "100%",
                    contentDescription = "Wednesday, rain, low 17 degrees, high 24 degrees",
                ),
            ),
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
