package com.nauhaan.skycast.ui.common

import com.nauhaan.skycast.core.designsystem.component.MetricVisual
import com.nauhaan.skycast.core.designsystem.component.SunPathReading
import com.nauhaan.skycast.core.designsystem.component.WeatherDetail
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailKind
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import java.time.Duration
import java.time.Instant
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
                visual = MetricVisual.Gauge((dewPointCelsius / temperatureCelsius).coerceIn(0.0, 1.0).toFloat()),
            ),
        )
    }

    return listOf(
        WeatherDetail(
            label = labels.humidity,
            value = "$humidityPercent%",
            kind = WeatherDetailKind.HUMIDITY,
            // Humidity is already a percentage, so its fraction is itself.
            visual = MetricVisual.Gauge((humidityPercent / PERCENT).toFloat()),
        ),
        WeatherDetail(
            label = labels.wind,
            value = windText,
            kind = WeatherDetailKind.WIND,
            // The direction has been in the model since the first commit and was never shown,
            // because a bar cannot draw a bearing. A compass can.
            visual = MetricVisual.Compass(
                degrees = windDirectionDegrees.toFloat(),
                cardinal = cardinalFor(windDirectionDegrees),
            ),
        ),
        WeatherDetail(
            label = labels.pressure,
            value = pressureText,
            kind = WeatherDetailKind.PRESSURE,
            // Scaled across the range a barometer realistically covers, so the arc moves
            // meaningfully day to day.
            visual = MetricVisual.Gauge(
                ((pressureHpa - LOW_PRESSURE_HPA) / PRESSURE_RANGE_HPA).coerceIn(0.0, 1.0).toFloat(),
            ),
        ),
        WeatherDetail(
            label = labels.visibility,
            value = "${visibility.toOneDecimalPlace()} ${visibilityUnit.symbol}",
            kind = WeatherDetailKind.VISIBILITY,
            // 10 km is the value OpenWeather reports for "clear", so it is effectively the ceiling.
            visual = MetricVisual.Gauge(
                (visibilityMetres / CLEAR_VISIBILITY_METRES).coerceIn(0.0, 1.0).toFloat(),
            ),
        ),
        WeatherDetail(
            label = labels.cloud,
            value = "$cloudinessPercent%",
            kind = WeatherDetailKind.CLOUD,
            visual = MetricVisual.Gauge((cloudinessPercent / PERCENT).toFloat()),
        ),
    ) + derived
}

/**
 * Everything the sun-path card needs, or `null` where the times are unusable.
 */
fun Weather.toSunPath(now: Instant = Instant.now(), riseSetDescription: String): SunPathReading? {
    val span = Duration.between(sunrise, sunset)
    if (span.isZero || span.isNegative) return null
    return SunPathReading(
        progress = (Duration.between(sunrise, now).toMillis().toDouble() / span.toMillis()).toFloat(),
        sunriseLabel = sunriseLabel(),
        sunsetLabel = sunsetLabel(),
        daylightLabel = daylightDuration.toHoursAndMinutes(),
        contentDescription = riseSetDescription,
    )
}

/** The location's own wall-clock sunrise. See [toSunPath]. */
fun Weather.sunriseLabel(): String = DateTimeFormatter.ofPattern(TIME_PATTERN).withZone(zoneOffset).format(sunrise)

/** The location's own wall-clock sunset. */
fun Weather.sunsetLabel(): String = DateTimeFormatter.ofPattern(TIME_PATTERN).withZone(zoneOffset).format(sunset)

/** How long the sun is up, as "14h 28m". */
fun Weather.daylightLabel(): String = daylightDuration.toHoursAndMinutes()

/**
 * The 16-point compass name for a bearing, so "west-northwest" is available and not just "west".
 *
 * Internal so its test can reach it.
 */
internal fun cardinalFor(degrees: Int): String {
    val points = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )
    // Normalised first: the API documents 0 to 360, but 360 and a negative are both representable,
    // and an out-of-range index would crash the screen.
    val normalised = ((degrees % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    val sector = (normalised / SECTOR_DEGREES).roundToInt() % points.size
    return points[sector]
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

private const val LOW_PRESSURE_HPA = 950.0
private const val PRESSURE_RANGE_HPA = 130.0
private const val CLEAR_VISIBILITY_METRES = 10_000.0

/** 24-hour clock: unambiguous, and the app is English-only for now. */
private const val TIME_PATTERN = "HH:mm"

/** 360° over sixteen compass points. */
private const val SECTOR_DEGREES = 22.5
private const val FULL_CIRCLE_DEGREES = 360
