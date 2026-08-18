package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.roundToInt

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

    /**
     * The lowest broken or overcast layer, in feet above the field: the **ceiling**.
     *
     * Not simply the lowest cloud. FEW and SCT layers are not a ceiling, which is why the
     * flight-category thresholds are defined against BKN and OVC only.
     */
    val ceilingFeet: Int?
        get() = clouds
            .filter { it.cover in CEILING_COVERS }
            .mapNotNull { it.baseFeet }
            .minOrNull()

    /**
     * Relative humidity, computed from the temperature and dew point.
     *
     * A METAR reports both but never the humidity, and the pair is the harder one to read: 26/14 says
     * "fairly dry" only to someone who does the arithmetic. Magnus, with the same coefficients the app
     * already uses to derive dew point from humidity for OpenWeather, the inverse of that calculation,
     * so the two cannot disagree.
     */
    val relativeHumidityPercent: Int?
        get() {
            val temperature = temperatureCelsius ?: return null
            val dewPoint = dewPointCelsius ?: return null
            val numerator = exp(MAGNUS_B * dewPoint / (MAGNUS_C + dewPoint))
            val denominator = exp(MAGNUS_B * temperature / (MAGNUS_C + temperature))
            return (PERCENT * numerator / denominator).roundToInt().coerceIn(0, PERCENT.toInt())
        }

    /**
     * How far the temperature is above the dew point, in degrees.
     *
     * The number pilots read for fog risk: as the spread closes on zero the air is saturating, and fog or
     * low cloud becomes likely. A spread of 1 °C at dusk is a different evening from a spread of 12 °C.
     */
    val dewPointSpreadCelsius: Double?
        get() {
            val temperature = temperatureCelsius ?: return null
            val dewPoint = dewPointCelsius ?: return null
            return temperature - dewPoint
        }

    /**
     * Density altitude, in feet, the altitude the air *behaves* like.
     *
     * Hot, low-pressure air is thin, and thin air lengthens a take-off run and cuts climb rate. On a
     * hot day an airfield at sea level can perform like one two thousand feet up.
     *
     * Uses the standard field approximation: pressure altitude from the altimeter setting (27 ft per
     * hectopascal from standard pressure), then 120 ft for every degree the air is above ISA for
     * that altitude. Labelled as approximate on screen.
     */
    val densityAltitudeFeet: Int?
        get() {
            val temperature = temperatureCelsius ?: return null
            val altimeter = altimeterHectopascals ?: return null
            val elevationFeet = elevationMetres * FEET_PER_METRE
            val pressureAltitude = elevationFeet + (STANDARD_PRESSURE_HPA - altimeter) * FEET_PER_HPA
            val isaTemperature = ISA_SEA_LEVEL_C - ISA_LAPSE_C_PER_1000_FT * (pressureAltitude / FEET_PER_THOUSAND)
            return (pressureAltitude + FEET_PER_DEGREE_ABOVE_ISA * (temperature - isaTemperature))
                .roundToInt()
        }

    companion object {
        val METAR_TTL: Duration = Duration.ofMinutes(30)

        /** Only these coverages form a ceiling. See [ceilingFeet]. */
        private val CEILING_COVERS = setOf("BKN", "OVC", "VV")

        // Magnus coefficients, matching DerivedReading's dew-point calculation.
        private const val MAGNUS_B = 17.62
        private const val MAGNUS_C = 243.12

        private const val FEET_PER_METRE = 3.28084
        private const val STANDARD_PRESSURE_HPA = 1013.25
        private const val FEET_PER_HPA = 27.0
        private const val ISA_SEA_LEVEL_C = 15.0
        private const val ISA_LAPSE_C_PER_1000_FT = 2.0
        private const val FEET_PER_DEGREE_ABOVE_ISA = 120.0
        private const val PERCENT = 100.0

        /** The lapse rate is quoted per thousand feet, so the altitude has to be divided by it. */
        private const val FEET_PER_THOUSAND = 1_000.0
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
    UNKNOWN("N/A"),
    ;

    companion object {
        fun from(code: String?): FlightCategory =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: UNKNOWN
    }
}
