package com.nauhaan.skycast.ui.common

import com.nauhaan.skycast.core.designsystem.component.WeatherDetail
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Formats a [Weather] into the secondary readings shown under the hero block.
 *
 * Lives in `ui` rather than `domain` because every decision here is presentational: how many
 * decimal places, which unit symbol, what time format. Labels arrive already resolved, so this
 * stays free of Android's `Context` and remains a plain testable function.
 */
@Suppress("LongParameterList") // Six labels, all required; a wrapper type would add no clarity.
fun Weather.toDetails(
    windUnit: WindSpeedUnit,
    humidityLabel: String,
    windLabel: String,
    pressureLabel: String,
    visibilityLabel: String,
    sunriseLabel: String,
    sunsetLabel: String,
): List<WeatherDetail> {
    // The location's zone, not the device's: London's sunrise is 04:49 in London, and
    // reporting it as 09:49 because the phone is five hours ahead is simply wrong.
    val timeFormat = DateTimeFormatter.ofPattern(TIME_PATTERN).withZone(zoneOffset)
    val wind = windUnit.convertFromMetresPerSecond(windSpeedMetresPerSecond)
    return listOf(
        WeatherDetail(humidityLabel, "$humidityPercent%"),
        WeatherDetail(windLabel, "${wind.toOneDecimalPlace()} ${windUnit.symbol}"),
        WeatherDetail(pressureLabel, "$pressureHpa hPa"),
        // The API reports metres; people read kilometres.
        WeatherDetail(
            visibilityLabel,
            "${(visibilityMetres / METRES_PER_KILOMETRE).toOneDecimalPlace()} km",
        ),
        WeatherDetail(sunriseLabel, timeFormat.format(sunrise)),
        WeatherDetail(sunsetLabel, timeFormat.format(sunset)),
    )
}

/** One decimal place, which is as much precision as these readings support. */
private fun Double.toOneDecimalPlace(): Double = (this * ONE_DECIMAL_SCALE).roundToInt() / ONE_DECIMAL_SCALE

private const val ONE_DECIMAL_SCALE = 10.0
private const val METRES_PER_KILOMETRE = 1000.0

/** 24-hour clock: unambiguous, and the app is English-only for now. */
private const val TIME_PATTERN = "HH:mm"
