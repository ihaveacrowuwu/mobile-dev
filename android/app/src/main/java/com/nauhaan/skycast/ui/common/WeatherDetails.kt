package com.nauhaan.skycast.ui.common

import com.nauhaan.skycast.core.designsystem.component.WeatherDetail
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailKind
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
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
    preferences: UserPreferences,
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

    val windUnit = preferences.windSpeedUnit
    val wind = windUnit.convertFromMetresPerSecond(windSpeedMetresPerSecond)
    val windText = if (windUnit.isWholeNumber) {
        // Beaufort is a force, not a speed: "5 Bft", never "5.0 Bft".
        "${wind.roundToInt()} ${windUnit.symbol}"
    } else {
        "${wind.toOneDecimalPlace()} ${windUnit.symbol}"
    }

    val pressureUnit = preferences.pressureUnit
    val pressure = pressureUnit.convertFromHectopascals(pressureHpa.toDouble())
    val pressureText = "${pressure.toPlaces(pressureUnit.decimalPlaces)} ${pressureUnit.symbol}"

    val visibilityUnit = preferences.visibilityUnit
    val visibility = visibilityUnit.convertFromMetres(visibilityMetres.toDouble())

    return listOf(
        WeatherDetail(
            label = humidityLabel,
            value = "$humidityPercent%",
            kind = WeatherDetailKind.HUMIDITY,
            // Humidity is already a percentage, so its fraction is itself.
            fraction = humidityPercent / PERCENT,
        ),
        WeatherDetail(
            label = windLabel,
            value = windText,
            kind = WeatherDetailKind.WIND,
            fraction = (windSpeedMetresPerSecond / STRONG_WIND_METRES_PER_SECOND).coerceIn(0.0, 1.0),
        ),
        WeatherDetail(
            label = pressureLabel,
            value = pressureText,
            kind = WeatherDetailKind.PRESSURE,
            // Scaled across the range a barometer realistically covers, so the indicator moves
            // meaningfully instead of sitting at the same spot every day.
            fraction = ((pressureHpa - LOW_PRESSURE_HPA) / PRESSURE_RANGE_HPA).coerceIn(0.0, 1.0),
        ),
        WeatherDetail(
            label = visibilityLabel,
            value = "${visibility.toOneDecimalPlace()} ${visibilityUnit.symbol}",
            kind = WeatherDetailKind.VISIBILITY,
            // 10 km is the value OpenWeather reports for "clear", so it is effectively the ceiling.
            fraction = (visibilityMetres / CLEAR_VISIBILITY_METRES).coerceIn(0.0, 1.0),
        ),
        WeatherDetail(
            label = sunriseLabel,
            value = timeFormat.format(sunrise),
            kind = WeatherDetailKind.SUNRISE,
        ),
        WeatherDetail(
            label = sunsetLabel,
            value = timeFormat.format(sunset),
            kind = WeatherDetailKind.SUNSET,
        ),
    )
}

/** One decimal place, which is as much precision as these readings support. */
private fun Double.toOneDecimalPlace(): Double = (this * ONE_DECIMAL_SCALE).roundToInt() / ONE_DECIMAL_SCALE

/** Formats to a fixed number of decimals. 0 gives a bare integer, not "1013.0". */
private fun Double.toPlaces(places: Int): String = if (places == 0) {
    roundToInt().toString()
} else {
    String.format(java.util.Locale.getDefault(), "%.${places}f", this)
}

private const val ONE_DECIMAL_SCALE = 10.0
private const val PERCENT = 100.0

/** Roughly a strong gale, the point past which the wind indicator should read as full. */
private const val STRONG_WIND_METRES_PER_SECOND = 25.0

private const val LOW_PRESSURE_HPA = 950.0
private const val PRESSURE_RANGE_HPA = 130.0
private const val CLEAR_VISIBILITY_METRES = 10_000.0

/** 24-hour clock: unambiguous, and the app is English-only for now. */
private const val TIME_PATTERN = "HH:mm"
