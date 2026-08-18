package com.nauhaan.skycast.ui.common

import com.nauhaan.skycast.core.designsystem.component.WeatherDetail
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailKind
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Formats a [Weather] into the secondary readings shown under the hero block.
 *
 * Lives in `ui`, not `domain`, because everything here is presentational: decimal places, unit
 * symbols, time format. Labels arrive already resolved, so this stays free of Android's `Context`
 * and remains a plain testable function.
 *
 * @param includeDerived adds dew point and length of day. Off for Home, on for the detail screen.
 */
fun Weather.toDetails(
    preferences: UserPreferences,
    labels: WeatherDetailLabels,
    includeDerived: Boolean = false,
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

    val derived = if (!includeDerived) {
        emptyList()
    } else {
        val temperatureUnit = preferences.temperatureUnit
        val dewPoint = temperatureUnit.convertFromCelsius(dewPointCelsius)
        listOf(
            WeatherDetail(
                label = labels.dewPoint,
                value = "${dewPoint.roundToInt()}${temperatureUnit.symbol}",
                kind = WeatherDetailKind.DEW_POINT,
                // Measured against the air temperature: a dew point close to it is what "muggy"
                // actually means, and a nearly-full bar says so without the meteorology.
                fraction = (dewPointCelsius / temperatureCelsius).coerceIn(0.0, 1.0),
            ),
            WeatherDetail(
                label = labels.daylight,
                value = daylightDuration.toHoursAndMinutes(),
                kind = WeatherDetailKind.DAYLIGHT,
                fraction = (daylightDuration.toMinutes() / MINUTES_IN_A_DAY).coerceIn(0.0, 1.0),
            ),
        )
    }

    return listOf(
        WeatherDetail(
            label = labels.humidity,
            value = "$humidityPercent%",
            kind = WeatherDetailKind.HUMIDITY,
            // Humidity is already a percentage, so its fraction is itself.
            fraction = humidityPercent / PERCENT,
        ),
        WeatherDetail(
            label = labels.wind,
            value = windText,
            kind = WeatherDetailKind.WIND,
            fraction = (windSpeedMetresPerSecond / STRONG_WIND_METRES_PER_SECOND).coerceIn(0.0, 1.0),
        ),
        WeatherDetail(
            label = labels.pressure,
            value = pressureText,
            kind = WeatherDetailKind.PRESSURE,
            // Scaled across the range a barometer realistically covers, so the indicator moves
            // meaningfully instead of sitting at the same spot every day.
            fraction = ((pressureHpa - LOW_PRESSURE_HPA) / PRESSURE_RANGE_HPA).coerceIn(0.0, 1.0),
        ),
        WeatherDetail(
            label = labels.visibility,
            value = "${visibility.toOneDecimalPlace()} ${visibilityUnit.symbol}",
            kind = WeatherDetailKind.VISIBILITY,
            // 10 km is the value OpenWeather reports for "clear", so it is effectively the ceiling.
            fraction = (visibilityMetres / CLEAR_VISIBILITY_METRES).coerceIn(0.0, 1.0),
        ),
        WeatherDetail(
            label = labels.sunrise,
            value = timeFormat.format(sunrise),
            kind = WeatherDetailKind.SUNRISE,
        ),
        WeatherDetail(
            label = labels.sunset,
            value = timeFormat.format(sunset),
            kind = WeatherDetailKind.SUNSET,
        ),
    ) + derived
}

/** "14h 28m", rather than a count of minutes. */
private fun Duration.toHoursAndMinutes(): String = "${toHours()}h ${toMinutesPart()}m"

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

/** The scale the daylight indicator reads against: a full 24 hours of sun. */
private const val MINUTES_IN_A_DAY = 24.0 * 60
