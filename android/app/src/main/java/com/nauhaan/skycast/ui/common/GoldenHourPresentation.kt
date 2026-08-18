package com.nauhaan.skycast.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.GoldenHourReading
import com.nauhaan.skycast.domain.model.GoldenHour
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.SolarCalculator
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The evening's light for this place, formatted.
 *
 * Returns `null` where there is nothing to say, such as a polar summer or a latitude where the sun
 * never reaches the angles that define these windows. The card then does not appear.
 */
@Composable
fun goldenHourReading(location: SavedLocation, zone: ZoneId, now: Instant = Instant.now()): GoldenHourReading? {
    // The coordinates come from the saved location, not from the weather: a reading carries a zone offset but
    // not a position, and these windows are entirely a function of where you are standing.
    val light = SolarCalculator.eveningLight(now, location.latitude, location.longitude, zone) ?: return null
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    val goldenRange = stringResource(
        R.string.golden_hour_range,
        formatter.format(light.goldenStart),
        formatter.format(light.goldenEnd),
    )
    val blueRange = stringResource(
        R.string.golden_hour_range,
        formatter.format(light.goldenEnd),
        formatter.format(light.blueEnd),
    )

    return GoldenHourReading(
        goldenLabel = stringResource(R.string.golden_hour),
        blueLabel = stringResource(R.string.blue_hour),
        goldenRangeLabel = goldenRange,
        blueRangeLabel = blueRange,
        progress = light.progress(now),
        contentDescription = stringResource(
            R.string.golden_hour_description,
            formatter.format(light.goldenStart),
            formatter.format(light.goldenEnd),
            formatter.format(light.blueEnd),
        ),
    )
}

/**
 * Where [now] sits across the whole golden-plus-blue span, or `null` before it starts and after it ends.
 *
 * `null` rather than a clamped 0 or 1: a marker parked at an end would say "it is happening" all afternoon
 * and all night, which is the opposite of what the band is for.
 */
private fun GoldenHour.progress(now: Instant): Float? {
    if (now.isBefore(goldenStart) || now.isAfter(blueEnd)) return null
    val total = Duration.between(goldenStart, blueEnd).seconds.toFloat()
    if (total <= 0f) return null
    return Duration.between(goldenStart, now).seconds.toFloat() / total
}
