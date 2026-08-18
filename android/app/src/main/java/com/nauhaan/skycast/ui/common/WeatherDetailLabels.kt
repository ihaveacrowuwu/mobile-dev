package com.nauhaan.skycast.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R

/**
 * The resolved display labels for the detail tiles.
 *
 * One value rather than eight parameters. `toDetails` took them one by one and needed a
 * `LongParameterList` suppression at six; the derived readings would have made it eight, at which
 * point call sites become impossible to read and easy to mis-order, every argument is a `String`,
 * so swapping two of them still compiles.
 */
data class WeatherDetailLabels(
    val humidity: String,
    val wind: String,
    val pressure: String,
    val visibility: String,
    val sunrise: String,
    val sunset: String,
    val cloud: String,
    val dewPoint: String,
    val daylight: String,
)

/** Resolves every tile label from resources in one call. */
@Composable
fun rememberWeatherDetailLabels(): WeatherDetailLabels = WeatherDetailLabels(
    humidity = stringResource(R.string.detail_humidity),
    wind = stringResource(R.string.detail_wind),
    pressure = stringResource(R.string.detail_pressure),
    visibility = stringResource(R.string.detail_visibility),
    sunrise = stringResource(R.string.detail_sunrise),
    sunset = stringResource(R.string.detail_sunset),
    cloud = stringResource(R.string.detail_cloud),
    dewPoint = stringResource(R.string.detail_dew_point),
    daylight = stringResource(R.string.detail_daylight),
)
