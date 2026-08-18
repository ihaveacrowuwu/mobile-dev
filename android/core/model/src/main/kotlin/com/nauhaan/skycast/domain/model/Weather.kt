package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.ln

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
    /**
     * The **observed location's** UTC offset, not the device's.
     *
     * Carried through the domain because sunrise and sunset are only meaningful as a wall
     * clock in the place they happen: rendering London's sunrise in a Maldivian phone's
     * timezone reports 09:49 for an event that London calls 04:49. Every `Instant` in this
     * model is unambiguous on its own; this is what turns one back into a local time.
     */
    val zoneOffset: ZoneOffset,
    /** When this record was written to the local cache. Drives staleness. */
    val cachedAt: Instant,
) {
    /** True while the sun is up at the observed location, which picks the day/night art. */
    val isDaytime: Boolean get() = observedAt in sunrise..sunset

    /**
     * How long the sun is up.
     *
     * Two timestamps the API already sends, subtracted, but "sunrise 05:50, sunset 20:18" asks
     * the reader to do arithmetic before it means anything, and "14h 28m" does not.
     */
    val daylightDuration: Duration
        get() = Duration.between(sunrise, sunset).coerceAtLeast(Duration.ZERO)

    /**
     * The temperature at which the air would start to condense, in Celsius.
     *
     * Derived rather than fetched: OpenWeather's free tier does not send it, and the Magnus
     * formula recovers it from temperature and relative humidity to well inside a degree over the
     * range any inhabited place sees. It is the reading that actually says whether the air will
     * feel muggy, 78% humidity means something very different at 5° than at 30°, and it is what
     * aviation weather reports quote alongside the temperature.
     */
    val dewPointCelsius: Double
        get() {
            val humidity = humidityPercent.coerceIn(1, 100) / 100.0
            val gamma = (MAGNUS_B * temperatureCelsius) / (MAGNUS_C + temperatureCelsius) + ln(humidity)
            return (MAGNUS_C * gamma) / (MAGNUS_B - gamma)
        }

    /**
     * Whether the cached copy is older than [ttl] and should be refreshed.
     * Staleness is a *presentation* concern, not an error: stale data is still shown.
     */
    fun isStale(now: Instant, ttl: Duration = CURRENT_WEATHER_TTL): Boolean = cachedAt.plus(ttl).isBefore(now)

    companion object {
        /** OpenWeather refreshes station data roughly every 10 minutes. */
        val CURRENT_WEATHER_TTL: Duration = Duration.ofMinutes(10)

        /** Magnus-formula coefficients, in the Sonntag1990 form. */
        private const val MAGNUS_B = 17.62
        private const val MAGNUS_C = 243.12
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
