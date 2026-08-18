package com.nauhaan.skycast.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.WeatherConditionBadge
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherPalette
import com.nauhaan.skycast.domain.model.HourlyForecast
import com.nauhaan.skycast.domain.model.TemperatureUnit
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * A horizontally scrollable strip of three-hourly readings around the present moment.
 *
 * A `LazyRow` supplies momentum, edge behaviour and accessibility scrolling. Past hours are
 * included, and the present is marked.
 *
 * Times are rendered in the **forecast location's** zone, not the device's: a Maldivian forecast
 * read from London must still show Maldivian hours.
 */
@Composable
fun HourlyStrip(
    hours: List<HourlyForecast>,
    zoneOffset: ZoneOffset,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    // Overridable because the detail screen shows only what is ahead, where "Through the day" does
    // not apply.
    title: String = stringResource(R.string.today_hourly_heading),
) {
    if (hours.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.sm),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.md),
            modifier = Modifier.nestedScroll(StripKeepsItsOwnGesture),
        ) {
            items(hours, key = { it.time.epochSecond }) { hour ->
                HourColumn(hour = hour, zoneOffset = zoneOffset, unit = unit, now = now)
            }
        }
    }
}

/**
 * Stops a scroll of the strip turning into a swipe to the next place.
 *
 * Compose chains nested scrolling: when the row reaches its end, the horizontal delta it could not
 * use travels up to the [HorizontalPager] behind it, and the *same* gesture carries on as a page
 * change.
 *
 * This consumes whatever the row left over, in the horizontal axis only, so the pager sees nothing
 * from a gesture that began here. Vertical is passed through untouched, so a drag up or down on the
 * strip still scrolls the page. Swiping anywhere else still changes place.
 *
 * `internal` rather than private so `HourlyStripGestureTest` can assert both halves.
 */
internal val StripKeepsItsOwnGesture = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        available.copy(y = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available.copy(y = 0f)
}

@Composable
private fun HourColumn(
    hour: HourlyForecast,
    zoneOffset: ZoneOffset,
    unit: TemperatureUnit,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val localTime = hour.time.atOffset(zoneOffset)
    val temperature = unit.convertFromCelsius(hour.temperatureCelsius).roundToInt()
    val isCurrent = hour.isCurrent(now)
    // Checked before `isPast`, not after. The reading covering this moment *started* in the past,
    // three-hourly readings always have, so testing "is it past?" first meant the current hour
    // rendered as a time like any other, and only its bold weight hinted otherwise. iOS had the
    // order right, which is how the difference showed up.
    val isPast = hour.time.isBefore(now) && !isCurrent
    val label = if (isCurrent) {
        stringResource(R.string.today_hourly_now)
    } else {
        TIME_FORMAT.format(localTime)
    }
    val rain = (hour.precipitationProbability * PERCENT).roundToInt()

    // The forecast carries no sunrise or sunset, so daylight is approximated by clock hour.
    // Getting this wrong shows a sun at 3 am: the parity bug WeatherConditionIconTest guards.
    val isDaytime = localTime.hour in DAWN_HOUR until DUSK_HOUR

    val announcement = buildString {
        append(stringResource(R.string.today_hourly_accessibility, label, temperature, unit.symbol))
        if (rain > 0) {
            append(", ")
            append(stringResource(R.string.day_detail_rain_accessibility, rain))
        }
    }

    Column(
        modifier = modifier
            .width(ColumnWidth)
            .clearAndSetSemantics { contentDescription = announcement },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            // Past readings recede rather than disappear: still legible, clearly behind us.
            color = if (isPast) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        )
        WeatherConditionBadge(
            condition = hour.condition,
            isDaytime = isDaytime,
            size = BadgeSize,
        )
        Text(
            text = "$temperature${unit.symbol}",
            style = MaterialTheme.typography.titleMedium,
        )
        // Only when there is something to say. An always-present "0%" is noise on nine of ten days.
        if (rain > 0) {
            Text(
                text = "$rain%",
                style = MaterialTheme.typography.labelSmall,
                color = weatherPalette.humidity,
            )
        }
    }
}

/**
 * Whether this reading is the one covering the present moment.
 *
 * Readings are three hours apart, so "now" is the most recent one that has already started.
 */
private fun HourlyForecast.isCurrent(now: Instant): Boolean {
    val start = time
    val end = time.plusSeconds(READING_INTERVAL_SECONDS)
    return !now.isBefore(start) && now.isBefore(end)
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ColumnWidth = 64.dp
private val BadgeSize = 36.dp
private const val PERCENT = 100
private const val DAWN_HOUR = 6
private const val DUSK_HOUR = 20
private const val READING_INTERVAL_SECONDS = 3L * 60 * 60
