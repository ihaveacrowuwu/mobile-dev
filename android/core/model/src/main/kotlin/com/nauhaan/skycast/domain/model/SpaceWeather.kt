package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant

/**
 * The state of the Earth's magnetic field, as NOAA reports it.
 *
 * One number carries almost all of it: **Kp**, a 0–9 index of geomagnetic disturbance. It decides how far
 * from the poles the auroral oval reaches, which is what decides whether there is any point going outside to
 * look. See [AuroraCalculator] for the part that turns Kp into an answer for a particular place.
 */
data class SpaceWeather(
    /** The most recent measured or estimated Kp. */
    val kpNow: Double,
    val observedAt: Instant,
    /** NOAA's storm scale, G1 to G5, when the disturbance is large enough to have one. */
    val stormLevel: String?,
    /** The three-hourly forecast ahead of [observedAt], soonest first. */
    val upcoming: List<KpPeriod>,
    val cachedAt: Instant,
) {
    /**
     * The highest Kp forecast for the next [withinHours] hours, and when.
     *
     * The figure worth planning an evening around: aurora is a "go outside at the right hour" event, and the
     * current Kp says nothing about whether the interesting part has happened yet.
     */
    fun peakAhead(withinHours: Long = FORECAST_WINDOW_HOURS): KpPeriod? {
        val limit = observedAt.plus(Duration.ofHours(withinHours))
        return upcoming.filter { !it.time.isAfter(limit) }.maxByOrNull { it.kp }
    }

    /**
     * Whether the cached copy is older than [ttl].
     *
     * Kp is issued every three hours and re-estimated far more often than that, so half an hour keeps the
     * figure current without spending requests on a number that has not moved.
     */
    fun isStale(now: Instant, ttl: Duration = SPACE_WEATHER_TTL): Boolean = cachedAt.plus(ttl).isBefore(now)

    companion object {
        val SPACE_WEATHER_TTL: Duration = Duration.ofMinutes(30)

        /** Tonight, roughly: far enough ahead to cover an evening, not so far that the forecast is guesswork. */
        const val FORECAST_WINDOW_HOURS = 24L
    }
}

/** One three-hour Kp period. */
data class KpPeriod(
    val time: Instant,
    val kp: Double,
    /** NOAA's storm scale for the period, when it has one. */
    val stormLevel: String?,
)
