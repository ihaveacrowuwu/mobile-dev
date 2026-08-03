package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * A multi-day forecast for one location, already grouped into days.
 *
 * OpenWeather's free forecast endpoint returns a flat list of 3-hourly readings;
 * the grouping into [ForecastDay] happens in the data layer's mapper so the UI
 * never has to do date arithmetic.
 */
data class Forecast(
    val locationId: Long,
    val locationName: String,
    val days: List<ForecastDay>,
    val cachedAt: Instant,
) {
    fun isStale(now: Instant, ttl: Duration = FORECAST_TTL): Boolean = cachedAt.plus(ttl).isBefore(now)

    companion object {
        /** Forecasts change slowly; a 3-hour TTL keeps well inside the free API quota. */
        val FORECAST_TTL: Duration = Duration.ofHours(3)
    }
}

/** One calendar day of the forecast, with its 3-hourly readings retained for the detail screen. */
data class ForecastDay(
    val date: LocalDate,
    val condition: WeatherCondition,
    val description: String,
    val iconCode: String,
    val minTemperatureCelsius: Double,
    val maxTemperatureCelsius: Double,
    val precipitationProbability: Double,
    val hourly: List<HourlyForecast>,
)

/** A single 3-hourly reading. */
data class HourlyForecast(
    val time: Instant,
    val condition: WeatherCondition,
    val iconCode: String,
    val temperatureCelsius: Double,
    val precipitationProbability: Double,
    val windSpeedMetresPerSecond: Double,
)
