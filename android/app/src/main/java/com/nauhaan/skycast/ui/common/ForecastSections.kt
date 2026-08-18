package com.nauhaan.skycast.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.DailyRangeList
import com.nauhaan.skycast.core.designsystem.component.TemperatureTrend
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.ui.detail.toDayRanges
import com.nauhaan.skycast.ui.detail.toTrendPoints
import com.nauhaan.skycast.ui.detail.trendDescription
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The forecast sections shared by Home and a place's detail screen.
 *
 * Each section draws nothing when its data cannot support it. A forecast can fail while the current
 * reading succeeds, since they are separate requests.
 */
@Composable
fun TemperatureTrendSection(forecast: Forecast, unit: TemperatureUnit, modifier: Modifier = Modifier) {
    // Not wrapped in `remember`: these derivations are themselves @Composable, because the day labels
    // they build come from string resources. Compose skips them when their arguments are unchanged, so
    // they do not re-run per frame, which is what `remember` would have been for.
    val points = forecast.toTrendPoints(unit)
    if (points.size < MINIMUM_TREND_POINTS) return

    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.detail_section_trend),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        TemperatureTrend(
            points = points,
            contentDescription = forecast.trendDescription(unit),
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
    }
}

/**
 * The five-day list, each row a button through to that day's hour-by-hour breakdown.
 *
 * These rows are the only way into the day-detail screen from Home.
 */
@Composable
fun DailyRangesSection(
    forecast: Forecast,
    unit: TemperatureUnit,
    onDaySelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = forecast.toDayRanges(unit)
    if (days.isEmpty()) return

    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.detail_section_days),
            modifier = Modifier.padding(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.md,
                bottom = Spacing.sm,
            ),
        )
        DailyRangeList(
            days = days,
            modifier = Modifier.padding(horizontal = Spacing.md),
            onDaySelected = { day -> onDaySelected(day.epochDay) },
        )
    }
}

/**
 * When the reading was taken.
 *
 * Provenance for a screen full of numbers, and it belongs wherever those numbers are shown, which is
 * now Home as well as the detail screen.
 */
@Composable
fun ObservedAtFooter(observedAt: Instant, modifier: Modifier = Modifier) {
    val observed = stringResource(
        R.string.detail_observed_at,
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(observedAt),
    )

    Text(
        text = observed,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A section title, styled once so the headings on either screen cannot drift apart. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Two points make a line, not a trend. */
private const val MINIMUM_TREND_POINTS = 3
