package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The light, as photographers reckon it.
 *
 * The API already gives sunrise and sunset. What it does not give, and what people actually plan an evening
 * around, is the hour when the light goes gold, and the short window after it when everything turns blue.
 * Both are defined by the sun's **altitude**, not by the clock:
 *
 * | Window | Sun's altitude |
 * | --- | --- |
 * | Golden hour | +6° down to −4° |
 * | Blue hour | −4° down to −6° |
 * | Civil twilight ends | −6° |
 *
 * Those angles are why the "hour" is not an hour: it is twenty minutes in the tropics and most of an evening
 * in Scotland in June, and only a calculation can tell you which.
 *
 * Computed on the device for the same reasons as [MoonCalculator], deterministic, keyless, offline, exact
 * for any date. The solar position is the standard low-precision form (Meeus ch. 25 and 13), which is good to
 * well under a minute for these purposes: this implementation reproduces London's published sunrise and
 * sunset for 19 August 2026 (05:52 and 20:14 BST) to the minute.
 *
 * The Swift twin is `SolarCalculator.swift`.
 */
object SolarCalculator {
    /** Golden hour begins as the sun drops past this altitude, and ends at [GOLDEN_HOUR_END_DEGREES]. */
    const val GOLDEN_HOUR_START_DEGREES = 6.0

    /** Golden hour ends and blue hour begins here, a little below the horizon. */
    const val GOLDEN_HOUR_END_DEGREES = -4.0

    /** Blue hour ends here, which is also the end of civil twilight. */
    const val BLUE_HOUR_END_DEGREES = -6.0

    /**
     * The evening light for the local day containing [instant].
     *
     * `null` when the sun never reaches these altitudes that day, such as a polar summer or winter.
     */
    fun eveningLight(instant: Instant, latitude: Double, longitude: Double, zone: ZoneId): GoldenHour? {
        val startOfDay = LocalDate.ofInstant(instant, zone).atStartOfDay(zone).toInstant()

        // Descending crossings only: the sun passes +6° twice a day, and the evening one is the second.
        val goldenStart = lastDescendingCrossing(startOfDay, GOLDEN_HOUR_START_DEGREES, latitude, longitude)
        val goldenEnd = lastDescendingCrossing(startOfDay, GOLDEN_HOUR_END_DEGREES, latitude, longitude)
        val blueEnd = lastDescendingCrossing(startOfDay, BLUE_HOUR_END_DEGREES, latitude, longitude)

        if (goldenStart == null || goldenEnd == null || blueEnd == null) return null
        return GoldenHour(goldenStart = goldenStart, goldenEnd = goldenEnd, blueEnd = blueEnd)
    }

    /** The sun's altitude above the horizon, in degrees. */
    fun altitudeDegrees(instant: Instant, latitude: Double, longitude: Double): Double {
        val days = julianDay(instant) - JULIAN_EPOCH_2000
        val meanLongitude = (MEAN_LONGITUDE_EPOCH + MEAN_LONGITUDE_PER_DAY * days) % FULL_TURN
        val meanAnomaly = radians((MEAN_ANOMALY_EPOCH + MEAN_ANOMALY_PER_DAY * days) % FULL_TURN)
        val eclipticLongitude = radians(
            meanLongitude + EQUATION_OF_CENTRE_1 * sin(meanAnomaly) + EQUATION_OF_CENTRE_2 * sin(2 * meanAnomaly),
        )
        val obliquity = radians(OBLIQUITY_DEGREES)

        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val hourAngle = radians((greenwichSiderealDegrees(days) + longitude) % FULL_TURN) - rightAscension
        val observerLatitude = radians(latitude)

        return degrees(
            asin(
                (
                    sin(observerLatitude) * sin(declination) +
                        cos(observerLatitude) * cos(declination) * cos(hourAngle)
                    ).coerceIn(-1.0, 1.0),
            ),
        )
    }

    /**
     * The last time in the day that the sun sinks past [target], or `null` if it never does.
     *
     * Descending specifically: every one of these altitudes is crossed twice, once climbing in the morning and
     * once falling in the evening, and pairing a morning crossing with an evening one would produce a golden
     * "hour" that lasted all day.
     */
    private fun lastDescendingCrossing(
        startOfDay: Instant,
        target: Double,
        latitude: Double,
        longitude: Double,
    ): Instant? {
        var found: Instant? = null
        var previousTime = startOfDay
        var previous = altitudeDegrees(previousTime, latitude, longitude) - target

        for (step in 1..STEPS_PER_DAY) {
            val current = startOfDay.plusSeconds(step * STEP_SECONDS)
            val value = altitudeDegrees(current, latitude, longitude) - target
            if (previous > 0 && value <= 0) {
                found = bisect(previousTime, current) { altitudeDegrees(it, latitude, longitude) - target }
            }
            previousTime = current
            previous = value
        }
        return found
    }

    private fun bisect(from: Instant, to: Instant, difference: (Instant) -> Double): Instant {
        var lower = from
        var upper = to
        repeat(BISECTION_STEPS) {
            val middle = lower.plusMillis((upper.toEpochMilli() - lower.toEpochMilli()) / 2)
            if (difference(lower) * difference(middle) <= 0) upper = middle else lower = middle
        }
        return lower.plusMillis((upper.toEpochMilli() - lower.toEpochMilli()) / 2)
    }

    private fun greenwichSiderealDegrees(daysSinceEpoch: Double): Double {
        val value = (SIDEREAL_EPOCH_DEGREES + SIDEREAL_DEGREES_PER_DAY * daysSinceEpoch) % FULL_TURN
        return if (value < 0) value + FULL_TURN else value
    }

    private fun julianDay(instant: Instant): Double = instant.toEpochMilli() / MILLIS_PER_DAY + JULIAN_UNIX_EPOCH

    private fun radians(value: Double) = value * PI / HALF_TURN

    private fun degrees(value: Double) = value * HALF_TURN / PI

    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0
    private const val JULIAN_EPOCH_2000 = 2_451_545.0
    private const val JULIAN_UNIX_EPOCH = 2_440_587.5
    private const val MILLIS_PER_DAY = 86_400_000.0

    // The sun's mean elements at J2000 and their daily rates, and sidereal time's, all published values
    // rather than chosen ones, which is why they are named rather than explained.
    private const val MEAN_LONGITUDE_EPOCH = 280.460
    private const val MEAN_LONGITUDE_PER_DAY = 0.9856474
    private const val MEAN_ANOMALY_EPOCH = 357.528
    private const val MEAN_ANOMALY_PER_DAY = 0.9856003
    private const val SIDEREAL_EPOCH_DEGREES = 280.46061837
    private const val SIDEREAL_DEGREES_PER_DAY = 360.98564736629
    private const val OBLIQUITY_DEGREES = 23.439
    private const val EQUATION_OF_CENTRE_1 = 1.915
    private const val EQUATION_OF_CENTRE_2 = 0.020

    /** A minute is fine here: every window this finds is minutes long at its shortest. */
    private const val STEP_SECONDS = 60L
    private const val STEPS_PER_DAY = 1_440
    private const val BISECTION_STEPS = 30
}

/**
 * The evening's light, as three instants.
 *
 * Golden hour runs from [goldenStart] to [goldenEnd]; blue hour picks up there and ends at [blueEnd], which
 * is also the end of civil twilight.
 */
data class GoldenHour(val goldenStart: Instant, val goldenEnd: Instant, val blueEnd: Instant) {
    val goldenDuration: Duration get() = Duration.between(goldenStart, goldenEnd)

    val blueDuration: Duration get() = Duration.between(goldenEnd, blueEnd)

    /** Whether [instant] falls inside the golden window. */
    fun isGolden(instant: Instant): Boolean = !instant.isBefore(goldenStart) && instant.isBefore(goldenEnd)

    /** Whether [instant] falls inside the blue window. */
    fun isBlue(instant: Instant): Boolean = !instant.isBefore(goldenEnd) && instant.isBefore(blueEnd)
}
