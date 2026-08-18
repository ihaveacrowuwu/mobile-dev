package com.nauhaan.skycast.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The Moon, computed from the date.
 *
 * The series are the standard truncated forms from Meeus, *Astronomical Algorithms* (2nd ed.),
 * chapters 22, 25 and 47, keeping the largest periodic terms only.
 *
 * Accuracy measured against PyEphem across every third day of 2026 and 24 principal phases.
 * Worst-case error: **0.36 percentage points** of illumination, **153 km** of distance,
 * **5.2 minutes** on a phase instant, and **under a minute** on moonrise for London and Malé.
 *
 * The Swift twin is `MoonCalculator.swift`, checked against the same reference values with the same
 * tolerances.
 */
object MoonCalculator {
    /** The mean interval between new moons, in days. */
    const val SYNODIC_MONTH = 29.530588853

    /**
     * The full picture for one instant at one place.
     *
     * @param zone the zone whose local day moonrise and moonset are searched within. Rise and set
     *   belong to a *day*, and which day depends on the observer's clock, not on UTC.
     */
    fun snapshot(instant: Instant, latitude: Double, longitude: Double, zone: ZoneId = ZoneOffset.UTC): MoonSnapshot {
        val elongation = elongationDegrees(instant)
        val position = moonPosition(centuries(instant))
        val riseSet = riseAndSet(instant, latitude, longitude, zone)

        return MoonSnapshot(
            instant = instant,
            illuminatedFraction = illuminatedFraction(elongation),
            elongationDegrees = elongation,
            ageDays = SYNODIC_MONTH * elongation / FULL_TURN,
            phase = phaseName(elongation),
            distanceKm = position.distanceKm,
            angularDiameterDegrees = angularDiameterDegrees(position.distanceKm),
            moonrise = riseSet.first,
            moonset = riseSet.second,
            upcomingPhases = upcomingPhases(instant),
        )
    }

    /**
     * The illuminated fraction of the disc, 0 at new and 1 at full.
     *
     * The phase angle Sun–Moon–Earth is 180° − elongation, and the illuminated fraction is
     * (1 + cos(phase angle)) / 2, which reduces to this.
     */
    fun illuminatedFraction(elongationDegrees: Double): Double = (1 - cos(radians(elongationDegrees))) / 2

    /**
     * Which of the eight named phases an elongation falls in.
     *
     * The principal phases get a narrow band around their exact angle rather than an eighth of the
     * circle each. Calling a moon 3° past full "full" is right; calling one 20° past full "full" is
     * not, and the drawn disc would visibly disagree with the label.
     */
    fun phaseName(elongationDegrees: Double): MoonPhaseName {
        val angle = normalise(elongationDegrees)
        val band = PRINCIPAL_BAND_DEGREES
        return when {
            angle < band || angle >= FULL_TURN - band -> MoonPhaseName.NEW
            angle < MoonPhaseName.FIRST_QUARTER_ELONGATION - band -> MoonPhaseName.WAXING_CRESCENT
            angle < MoonPhaseName.FIRST_QUARTER_ELONGATION + band -> MoonPhaseName.FIRST_QUARTER
            angle < MoonPhaseName.FULL_ELONGATION - band -> MoonPhaseName.WAXING_GIBBOUS
            angle < MoonPhaseName.FULL_ELONGATION + band -> MoonPhaseName.FULL
            angle < MoonPhaseName.LAST_QUARTER_ELONGATION - band -> MoonPhaseName.WANING_GIBBOUS
            angle < MoonPhaseName.LAST_QUARTER_ELONGATION + band -> MoonPhaseName.LAST_QUARTER
            else -> MoonPhaseName.WANING_CRESCENT
        }
    }

    /** The Moon's elongation from the Sun in ecliptic longitude, 0–360°. */
    fun elongationDegrees(instant: Instant): Double {
        val t = centuries(instant)
        return normalise(moonPosition(t).longitude - sunLongitude(t))
    }

    /** The next four principal phases after [instant], soonest first. */
    fun upcomingPhases(instant: Instant): List<PrincipalPhase> = MoonPhaseName.entries
        .mapNotNull { name ->
            val target = name.principalElongation ?: return@mapNotNull null
            nextTime(target, instant)?.let { PrincipalPhase(name, it) }
        }
        .sortedBy { it.instant }

    /**
     * The next instant after [after] at which the Moon's elongation equals [elongation].
     *
     * Found by scanning for a sign change in the wrapped difference, then bisecting. Bisection rather
     * than Meeus's direct phase series because it reuses the position model already here, one set of
     * equations to be right about instead of two, and it cannot drift out of agreement with the phase
     * the rest of the screen draws.
     */
    fun nextTime(elongation: Double, after: Instant, searchDays: Long = DEFAULT_SEARCH_DAYS): Instant? {
        // Wrapped to −180…180 so the root is a clean sign change rather than a 360° cliff.
        fun difference(at: Instant) = normalise(elongationDegrees(at) - elongation + HALF_TURN) - HALF_TURN

        var lower = after
        var lowerValue = difference(lower)
        val limit = after.plusSeconds(searchDays * SECONDS_PER_DAY)

        while (lower.isBefore(limit)) {
            val upper = lower.plusSeconds(SCAN_STEP_SECONDS)
            val upperValue = difference(upper)
            if (lowerValue < 0 && upperValue >= 0) {
                return bisect(lower, upper, ::difference)
            }
            lower = upper
            lowerValue = upperValue
        }
        return null
    }

    /**
     * Moonrise and moonset within the local day containing [instant].
     *
     * Both are nullable and independently so: at high latitudes the Moon can rise without setting
     * inside one day, or neither. A day can also legitimately contain a set belonging to the previous
     * night's moon, which is why the two are searched for separately rather than assumed to be a pair.
     */
    fun riseAndSet(instant: Instant, latitude: Double, longitude: Double, zone: ZoneId): Pair<Instant?, Instant?> {
        val start = LocalDate.ofInstant(instant, zone).atStartOfDay(zone).toInstant()

        fun aboveHorizon(at: Instant) = altitudeDegrees(at, latitude, longitude) - HORIZON_DEGREES

        var rise: Instant? = null
        var set: Instant? = null
        var previous = start
        var previousAltitude = aboveHorizon(previous)

        for (step in 1..RISE_SET_STEPS) {
            val current = start.plusSeconds(step * RISE_SET_STEP_SECONDS)
            val currentAltitude = aboveHorizon(current)

            if (previousAltitude < 0 && currentAltitude >= 0 && rise == null) {
                rise = bisect(previous, current, ::aboveHorizon)
            } else if (previousAltitude >= 0 && currentAltitude < 0 && set == null) {
                set = bisect(previous, current, ::aboveHorizon)
            }
            previous = current
            previousAltitude = currentAltitude
        }
        return rise to set
    }

    /** The Moon's altitude above the true horizon, in degrees. */
    fun altitudeDegrees(instant: Instant, latitude: Double, longitude: Double): Double {
        val t = centuries(instant)
        val position = moonPosition(t)
        val (rightAscension, declination) = equatorial(position.longitude, position.latitude, t)
        val hourAngle = radians(normalise(greenwichSiderealDegrees(julianDay(instant)) + longitude)) -
            rightAscension
        val observerLatitude = radians(latitude)
        val sine = sin(observerLatitude) * sin(declination) +
            cos(observerLatitude) * cos(declination) * cos(hourAngle)
        return degrees(asin(sine.coerceIn(-1.0, 1.0)))
    }

    /** Ecliptic longitude and latitude in degrees, and distance in kilometres. */
    data class MoonPosition(val longitude: Double, val latitude: Double, val distanceKm: Double)

    /**
     * Meeus ch. 47, truncated to the dominant periodic terms.
     *
     * The coefficients are the published ones, left as literals so they can be checked against the
     * book.
     */
    @Suppress("LongMethod", "MagicNumber")
    fun moonPosition(centuries: Double): MoonPosition {
        val t = centuries

        // Mean elements.
        val meanLongitude = 218.3164477 + 481_267.88123421 * t - 0.0015786 * t * t
        val meanElongation = 297.8501921 + 445_267.1114034 * t - 0.0018819 * t * t
        val sunAnomaly = 357.5291092 + 35_999.0502909 * t - 0.0001536 * t * t
        val moonAnomaly = 134.9633964 + 477_198.8675055 * t + 0.0087414 * t * t
        val argumentOfLatitude = 93.2720950 + 483_202.0175233 * t - 0.0036539 * t * t

        val d = radians(meanElongation)
        val m = radians(sunAnomaly)
        val mp = radians(moonAnomaly)
        val f = radians(argumentOfLatitude)

        val longitude = meanLongitude +
            6.288774 * sin(mp) +
            1.274027 * sin(2 * d - mp) +
            0.658314 * sin(2 * d) +
            0.213618 * sin(2 * mp) -
            0.185116 * sin(m) -
            0.114332 * sin(2 * f) +
            0.058793 * sin(2 * d - 2 * mp) +
            0.057066 * sin(2 * d - m - mp) +
            0.053322 * sin(2 * d + mp) +
            0.045758 * sin(2 * d - m) -
            0.040923 * sin(m - mp) -
            0.034720 * sin(d) -
            0.030383 * sin(m + mp) +
            0.015327 * sin(2 * d - 2 * f) -
            0.012528 * sin(mp + 2 * f) +
            0.010980 * sin(mp - 2 * f)

        val latitude = 5.128122 * sin(f) +
            0.280602 * sin(mp + f) +
            0.277693 * sin(mp - f) +
            0.173237 * sin(2 * d - f) +
            0.055413 * sin(2 * d - mp + f) +
            0.046271 * sin(2 * d - mp - f) +
            0.032573 * sin(2 * d + f) +
            0.017198 * sin(2 * mp + f)

        val distanceKm = 385_000.56 -
            20_905.355 * cos(mp) -
            3_699.111 * cos(2 * d - mp) -
            2_955.968 * cos(2 * d) -
            569.925 * cos(2 * mp) +
            48.888 * cos(m) -
            3.149 * cos(2 * f) +
            246.158 * cos(2 * d - 2 * mp) -
            152.138 * cos(2 * d - m - mp) -
            170.733 * cos(2 * d + mp) -
            204.586 * cos(2 * d - m) -
            129.620 * cos(m - mp) +
            108.743 * cos(d) +
            104.755 * cos(m + mp) +
            79.661 * cos(mp - 2 * f) +
            10.321 * cos(2 * d - 2 * f)

        return MoonPosition(normalise(longitude), latitude, distanceKm)
    }

    /** The Sun's apparent ecliptic longitude in degrees. Meeus ch. 25, low-precision form. */
    @Suppress("MagicNumber")
    fun sunLongitude(centuries: Double): Double {
        val t = centuries
        val meanLongitude = 280.46646 + 36_000.76983 * t + 0.0003032 * t * t
        val meanAnomaly = radians(357.52911 + 35_999.05029 * t - 0.0001537 * t * t)
        val centre = (1.914602 - 0.004817 * t) * sin(meanAnomaly) +
            (0.019993 - 0.000101 * t) * sin(2 * meanAnomaly) +
            0.000289 * sin(3 * meanAnomaly)
        return normalise(meanLongitude + centre)
    }

    /** Ecliptic to equatorial, in radians. Meeus ch. 13. */
    @Suppress("MagicNumber")
    private fun equatorial(longitude: Double, latitude: Double, centuries: Double): Pair<Double, Double> {
        val obliquity = radians(23.439291 - 0.0130042 * centuries)
        val l = radians(longitude)
        val b = radians(latitude)
        val rightAscension = atan2(
            sin(l) * cos(obliquity) - tan(b) * sin(obliquity),
            cos(l),
        )
        val declination = asin(sin(b) * cos(obliquity) + cos(b) * sin(obliquity) * sin(l))
        return (if (rightAscension < 0) rightAscension + 2 * PI else rightAscension) to declination
    }

    /** Greenwich mean sidereal time in degrees. Meeus ch. 12. */
    @Suppress("MagicNumber")
    private fun greenwichSiderealDegrees(julianDay: Double): Double =
        normalise(280.46061837 + 360.98564736629 * (julianDay - JULIAN_EPOCH_2000))

    private fun julianDay(instant: Instant): Double = instant.toEpochMilli() / MILLIS_PER_DAY + JULIAN_UNIX_EPOCH

    /** Julian centuries since J2000.0, the argument every series above takes. */
    private fun centuries(instant: Instant): Double = (julianDay(instant) - JULIAN_EPOCH_2000) / DAYS_PER_JULIAN_CENTURY

    /**
     * Narrows a bracketed root to well under a second.
     *
     * 40 halvings of a 10-minute bracket lands far inside floating-point noise; the loop is cheap and
     * a fixed count cannot fail to terminate the way a tolerance test can.
     */
    private fun bisect(from: Instant, to: Instant, difference: (Instant) -> Double): Instant {
        var lower = from
        var upper = to
        repeat(BISECTION_STEPS) {
            val middle = lower.plusMillis((upper.toEpochMilli() - lower.toEpochMilli()) / 2)
            if (difference(lower) * difference(middle) <= 0) upper = middle else lower = middle
        }
        return lower.plusMillis((upper.toEpochMilli() - lower.toEpochMilli()) / 2)
    }

    private fun angularDiameterDegrees(distanceKm: Double): Double = degrees(2 * atan(MOON_RADIUS_KM / distanceKm))

    private fun normalise(value: Double): Double {
        val remainder = value % FULL_TURN
        return if (remainder < 0) remainder + FULL_TURN else remainder
    }

    private fun radians(value: Double): Double = value * PI / HALF_TURN

    private fun degrees(value: Double): Double = value * HALF_TURN / PI

    /**
     * How far either side of a principal phase still counts as that phase.
     *
     * About 12 hours of elongation. Wide enough that "Full moon" is showing on the evening people go
     * out to look at it, narrow enough that the label always matches the drawn disc.
     */
    private const val PRINCIPAL_BAND_DEGREES = 6.0

    /**
     * The altitude at which the Moon is considered to rise or set.
     *
     * Meeus's standard +0.125°, which nets refraction at the horizon (−34′) against the Moon's
     * semidiameter and mean parallax (+57′). Using 0 here instead would put every rise time out by
     * roughly a minute at London's latitude and considerably more nearer the poles.
     */
    private const val HORIZON_DEGREES = 0.125

    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0
    private const val JULIAN_EPOCH_2000 = 2_451_545.0
    private const val JULIAN_UNIX_EPOCH = 2_440_587.5
    private const val DAYS_PER_JULIAN_CENTURY = 36_525.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val SECONDS_PER_DAY = 86_400L
    private const val MOON_RADIUS_KM = 1_737.4

    /** Far enough ahead to contain the next of any principal phase, which recur every ~29.5 days. */
    private const val DEFAULT_SEARCH_DAYS = 40L

    private const val SCAN_STEP_SECONDS = 6L * 3_600L
    private const val RISE_SET_STEP_SECONDS = 10L * 60L
    private const val RISE_SET_STEPS = 144
    private const val BISECTION_STEPS = 40
}
