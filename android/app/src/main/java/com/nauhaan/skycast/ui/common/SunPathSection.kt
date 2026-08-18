package com.nauhaan.skycast.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.SunPathCard
import com.nauhaan.skycast.domain.model.Weather

/**
 * The sun-path card, with its labels resolved.
 *
 * A thin wrapper so both Home and the location-detail screen render the same card from a [Weather]
 * without either of them knowing how the reading is formatted, and so the string resources stay in
 * the `ui` layer where a `Context` is available.
 */
@Composable
fun SunPathSection(weather: Weather, modifier: Modifier = Modifier) {
    val reading = weather.toSunPath(
        riseSetDescription = stringResource(
            R.string.detail_sun_path,
            weather.sunriseLabel(),
            weather.sunsetLabel(),
            weather.daylightLabel(),
        ),
    ) ?: return

    SunPathCard(reading = reading, modifier = modifier)
}
