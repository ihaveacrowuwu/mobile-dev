package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A decoded aviation routine weather report from the nearest reporting airport.
 *
 * METAR is the observation format pilots read before flying: issued by an airport, usually hourly,
 * in a fixed coded line, measured at a runway and quoted in aviation units.
 *
 * The raw line is kept alongside the decoded fields as the provenance for them.
 *
 * A **domain** model: no networking types, no Android. Values are stored in the units the report
 * uses, since knots and hectopascals are what the observation is issued in.
 */
data class MetarReport(
    /** The four-letter ICAO code, e.g. `EGLL`. */
    val stationId: String,
    /** The airport's own name, e.g. "London/Heathrow Intl, EN, GB". */
    val stationName: String,
    /** How far the station is from the saved location, in kilometres. */
    val distanceKm: Double,
    val latitude: Double,
    val longitude: Double,
    /** Field elevation in metres. */
    val elevationMetres: Int,
    val observedAt: Instant,
    val temperatureCelsius: Double?,
    val dewPointCelsius: Double?,
    /** Degrees the wind blows *from*, or `null` when the report says variable. */
    val windDirectionDegrees: Int?,
    val windSpeedKnots: Int?,
    /** Statute miles. `null` when the report omits it. */
    val visibilityStatuteMiles: Double?,
    /** Whether the visibility figure is a floor rather than a measurement, i.e. "10+". */
    val visibilityIsOrGreater: Boolean,
    /** Altimeter setting in hectopascals, the "Q1010" group. */
    val altimeterHectopascals: Double?,
    val clouds: List<CloudLayer>,
    val flightCategory: FlightCategory,
    /** The report exactly as issued. */
    val raw: String,
    /** When this record was written to the local cache. Drives staleness. */
    val cachedAt: Instant,
) {
    /**
     * Whether the cached copy is older than [ttl].
     *
     * Thirty minutes rather than the ten used for current weather: a METAR is issued on the hour
     * (and on the half hour as a SPECI when conditions change), so polling faster cannot produce a
     * newer observation, it only spends requests.
     */
    fun isStale(now: Instant, ttl: Duration = METAR_TTL): Boolean = cachedAt.plus(ttl).isBefore(now)

    /** How old the observation itself is, which is what a pilot actually cares about. */
    fun age(now: Instant): Duration = Duration.between(observedAt, now)

    companion object {
        val METAR_TTL: Duration = Duration.ofMinutes(30)
    }
}

/** One reported cloud layer. */
data class CloudLayer(
    /** The coverage abbreviation as issued: FEW, SCT, BKN, OVC, CLR, SKC. */
    val cover: String,
    /** Height of the layer's base above the field, in feet. `null` for clear skies. */
    val baseFeet: Int?,
)

/**
 * The flight-rules category the observation falls into.
 *
 * It decides whether a flight can be made under visual rules. The thresholds are the US standard.
 * The API computes the category itself, and its string is mapped here rather than re-derived, so
 * the badge cannot disagree with the source.
 */
enum class FlightCategory(val label: String) {
    /** Visual: ceiling above 3000 ft and visibility above 5 miles. */
    VFR("VFR"),

    /** Marginal visual: ceiling 1000–3000 ft or visibility 3–5 miles. */
    MVFR("MVFR"),

    /** Instrument: ceiling 500–1000 ft or visibility 1–3 miles. */
    IFR("IFR"),

    /** Low instrument: below 500 ft or below 1 mile. */
    LIFR("LIFR"),

    /** The report did not include one. */
    UNKNOWN(""),
    ;

    companion object {
        fun from(code: String?): FlightCategory =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: UNKNOWN
    }
}
