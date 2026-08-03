package com.nauhaan.skycast.domain.model

import java.time.Instant

/**
 * Current weather conditions for a single location.
 *
 * This is a **domain** model: it contains no framework types, no nullable
 * "maybe the API sent it" fields, and temperatures are always stored in Celsius.
 * Conversion to the user's preferred unit happens in the presentation layer so
 * that a settings change re-renders from cache without a network call.
 */
data class Weather(
    val locationId: Long,
    val locationName: String,
    val condition: WeatherCondition,
    val description: String,
    val iconCode: String,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val minTemperatureCelsius: Double,
    val maxTemperatureCelsius: Double,
    val humidityPercent: Int,
    val pressureHpa: Int,
    val windSpeedMetresPerSecond: Double,
    val windDirectionDegrees: Int,
    val cloudinessPercent: Int,
    val visibilityMetres: Int,
    val sunrise: Instant,
    val sunset: Instant,
    val observedAt: Instant,
    /** When this record was written to the local cache. Drives staleness. */
    val cachedAt: Instant,
) {
    /** True while the sun is up at the observed location, which picks the day/night art. */
    val isDaytime: Boolean get() = observedAt in sunrise..sunset

    /**
     * Whether the cached copy is older than [ttl] and should be refreshed.
     * Staleness is a *presentation* concern, not an error: stale data is still shown.
     */
    fun isStale(now: Instant, ttl: java.time.Duration = CURRENT_WEATHER_TTL): Boolean = cachedAt.plus(ttl).isBefore(now)

    companion object {
        /** OpenWeather refreshes station data roughly every 10 minutes. */
        val CURRENT_WEATHER_TTL: java.time.Duration = java.time.Duration.ofMinutes(10)
    }
}

/**
 * The condition groups SkyCast renders distinct artwork for.
 *
 * A closed set rather than the raw OpenWeather integer, so the compiler guarantees every branch of
 * the UI handles every condition.
 */
enum class WeatherCondition {
    CLEAR,
    CLOUDS,
    RAIN,
    DRIZZLE,
    THUNDERSTORM,
    SNOW,
    MIST,
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps an OpenWeather condition id to a [WeatherCondition].
         * See https://openweathermap.org/weather-conditions for the id ranges.
         */
        fun fromOpenWeatherId(id: Int): WeatherCondition = when (id) {
            in 200..232 -> THUNDERSTORM
            in 300..321 -> DRIZZLE
            in 500..531 -> RAIN
            in 600..622 -> SNOW
            in 700..781 -> MIST
            800 -> CLEAR
            in 801..804 -> CLOUDS
            else -> UNKNOWN
        }
    }
}
