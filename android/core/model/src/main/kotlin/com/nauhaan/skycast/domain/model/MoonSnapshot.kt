package com.nauhaan.skycast.domain.model

import java.time.Duration
import java.time.Instant

/**
 * The eight phase names people actually use.
 *
 * The four *principal* phases are instants, the Moon is exactly full for a moment. The four
 * intermediate ones are the stretches between them. That asymmetry is why [MoonSnapshot.phase] is
 * derived from a range while [PrincipalPhase] carries a precise [Instant].
 */
enum class MoonPhaseName {
    NEW,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT,
    ;

    /** The elongation at which this phase occurs, or `null` for the four that are spans. */
    val principalElongation: Double?
        get() = when (this) {
            NEW -> NEW_ELONGATION
            FIRST_QUARTER -> FIRST_QUARTER_ELONGATION
            FULL -> FULL_ELONGATION
            LAST_QUARTER -> LAST_QUARTER_ELONGATION
            else -> null
        }

    val isPrincipal: Boolean get() = principalElongation != null

    companion object {
        /** New moon: the Moon and the Sun share an ecliptic longitude. */
        const val NEW_ELONGATION = 0.0

        /** A quarter of the way round, and half lit. */
        const val FIRST_QUARTER_ELONGATION = 90.0

        /** Opposite the Sun, and fully lit. */
        const val FULL_ELONGATION = 180.0

        /** Three quarters round, half lit again, and the other half this time. */
        const val LAST_QUARTER_ELONGATION = 270.0
    }
}

/** How close the Moon is, as a band rather than a number. */
enum class MoonDistanceBand {
    VERY_CLOSE,
    CLOSER,
    AVERAGE,
    FURTHER,
    VERY_FAR,
}

/** One dated principal phase, for the "coming up" list. */
data class PrincipalPhase(val name: MoonPhaseName, val instant: Instant)

/**
 * Everything the Moon tab knows, for one instant at one place.
 *
 * Computed on the device, never fetched. Only [moonrise] and [moonset] depend on where you are; the
 * rest are the same for everybody on Earth, so the type carries a location-independent core and two
 * nullables.
 *
 * The Swift twin is `MoonSnapshot.swift`.
 */
data class MoonSnapshot(
    val instant: Instant,
    /** 0 at new, 1 at full. */
    val illuminatedFraction: Double,
    /**
     * The Moon's elongation from the Sun, 0–360°. 0 is new, 180 is full.
     *
     * Exposed rather than kept private because the phase name, the illuminated fraction and the
     * drawn terminator are all derived from it, three things from one value cannot disagree.
     */
    val elongationDegrees: Double,
    /** Days since the last new moon. */
    val ageDays: Double,
    val phase: MoonPhaseName,
    /** Centre-to-centre, in kilometres. */
    val distanceKm: Double,
    /** How wide the Moon looks, in degrees. Varies by about 12% over a month. */
    val angularDiameterDegrees: Double,
    /**
     * `null` when the Moon does not cross the horizon on this date at this latitude, which happens,
     * and is not an error.
     */
    val moonrise: Instant?,
    val moonset: Instant?,
    /** The next four principal phases, soonest first. */
    val upcomingPhases: List<PrincipalPhase>,
) {
    /** Growing towards full rather than shrinking towards new. */
    val isWaxing: Boolean get() = elongationDegrees < HALF_TURN

    /** Position through the lunar month, 0 at new and 1 at the next new. */
    val cycleFraction: Double get() = elongationDegrees / FULL_TURN

    val illuminatedPercent: Int get() = Math.round(illuminatedFraction * PERCENT).toInt()

    /**
     * Where tonight's distance sits between the closest and furthest the Moon gets, 0–1.
     *
     * Against the *extreme* perigee and apogee rather than the mean ones, so the gauge never pins at
     * either end.
     */
    val distanceFraction: Double
        get() = ((distanceKm - PERIGEE_KM) / (APOGEE_KM - PERIGEE_KM)).coerceIn(0.0, 1.0)

    /**
     * Which part of its range tonight's distance falls in.
     *
     * The band, not the wording: the thresholds are shared with iOS and unit-tested on both platforms,
     * while the sentence each one turns into belongs to the UI, where it can be a string resource.
     *
     * The extremes are the outer tenth at each end. They were the outer fifth first, which called a
     * perfectly ordinary 396,000 km "unusually far", visible in a screenshot, and wrong: a reading at
     * the 80th percentile of a range the Moon sweeps every month is not unusual by any reading of the
     * word.
     */
    val distanceBand: MoonDistanceBand
        get() = when {
            distanceFraction < VERY_CLOSE_BELOW -> MoonDistanceBand.VERY_CLOSE
            distanceFraction < CLOSER_BELOW -> MoonDistanceBand.CLOSER
            distanceFraction < AVERAGE_BELOW -> MoonDistanceBand.AVERAGE
            distanceFraction < FURTHER_BELOW -> MoonDistanceBand.FURTHER
            else -> MoonDistanceBand.VERY_FAR
        }

    /** The next full moon, for the countdown. */
    val nextFullMoon: PrincipalPhase? get() = upcomingPhases.firstOrNull { it.name == MoonPhaseName.FULL }

    /**
     * How long the Moon is above the horizon, when both ends are known.
     *
     * A set *before* the rise belongs to the previous night's moon, so the span runs to tomorrow's
     * set instead, otherwise the figure comes out negative.
     */
    val timeAboveHorizon: Duration?
        get() {
            val rise = moonrise ?: return null
            val set = moonset ?: return null
            val span = Duration.between(rise, set)
            return if (span.isNegative) span.plusDays(1) else span
        }

    companion object {
        /** Closest the Moon comes, in kilometres. */
        const val PERIGEE_KM = 356_500.0

        /** Furthest the Moon gets, in kilometres. */
        const val APOGEE_KM = 406_700.0

        private const val HALF_TURN = 180.0
        private const val FULL_TURN = 360.0
        private const val PERCENT = 100

        // The distance bands. The outer tenth at each end counts as unusual; see [distanceBand].
        private const val VERY_CLOSE_BELOW = 0.10
        private const val CLOSER_BELOW = 0.45
        private const val AVERAGE_BELOW = 0.55
        private const val FURTHER_BELOW = 0.90
    }
}
