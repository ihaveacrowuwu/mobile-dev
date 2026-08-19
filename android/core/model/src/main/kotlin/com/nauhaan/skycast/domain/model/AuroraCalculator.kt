package com.nauhaan.skycast.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Whether the aurora is worth going outside for, at a particular place.
 *
 * ## The two parts
 *
 * **Where the aurora is.** The auroral oval sits around the *geomagnetic* pole, not the geographic
 * one, and reaches further from that pole the more disturbed the field is. NOAA's published
 * equatorward edge against Kp is [EQUATORWARD_BOUNDARY_BY_KP] below, running from 66.5° of
 * geomagnetic latitude at Kp 0 down to 48.1° at Kp 9.
 *
 * **Where you are.** Geomagnetic latitude, not the latitude on a map. London and Calgary sit at
 * almost the same geographic latitude but at very different geomagnetic ones.
 *
 * ## Accuracy
 *
 * The pole is taken as fixed, which costs a few tenths of a degree per year against bands that are
 * whole degrees wide.
 *
 * The **centred dipole** approximation is good in the north, where the field is close to a dipole:
 * London 53.4° against ~54°, Tromsø 67.4° against ~67°, Anchorage 61.9° against ~61°. The southern
 * field is not a dipole, so southern latitudes are accurate only to several degrees. Results are
 * therefore reported as coarse bands rather than a percentage.
 *
 * The Swift twin is `AuroraCalculator.swift`.
 */
object AuroraCalculator {
    /**
     * Geomagnetic latitude for a place, in degrees.
     *
     * Standard spherical transform about the geomagnetic pole. Checked against published values: London comes
     * out at 53.4° against a published ~54°, Tromsø 67.4° against ~67°, Anchorage 61.9° against ~61°.
     */
    fun geomagneticLatitude(latitude: Double, longitude: Double): Double {
        val observerLatitude = radians(latitude)
        val poleLatitude = radians(POLE_LATITUDE)
        val relativeLongitude = radians(longitude - POLE_LONGITUDE)
        return degrees(
            asin(
                (
                    sin(observerLatitude) * sin(poleLatitude) +
                        cos(observerLatitude) * cos(poleLatitude) * cos(relativeLongitude)
                    ).coerceIn(-1.0, 1.0),
            ),
        )
    }

    /**
     * The equatorward edge of the auroral oval at this Kp, in degrees of geomagnetic latitude.
     *
     * Interpolated between NOAA's whole-Kp values, because the reported index is not an integer, a Kp of
     * 4.67 sits two thirds of the way from 4 to 5, and rounding it away would move the boundary by a degree.
     */
    fun equatorwardBoundary(kp: Double): Double {
        val clamped = kp.coerceIn(KP_MINIMUM.toDouble(), KP_MAXIMUM.toDouble())
        val lower = clamped.toInt()
        val upper = minOf(lower + 1, KP_MAXIMUM)
        val fraction = clamped - lower
        return EQUATORWARD_BOUNDARY_BY_KP[lower] +
            (EQUATORWARD_BOUNDARY_BY_KP[upper] - EQUATORWARD_BOUNDARY_BY_KP[lower]) * fraction
    }

    /**
     * How likely the aurora is to be visible from [geomagneticLatitude] at this [kp].
     *
     * The bands are wider than the oval itself: aurora happens 100 to 300 km up, so it can be seen
     * from a good way equatorward of where it actually is, low on the northern horizon. That is why
     * [AuroraChance.FAINT_ON_HORIZON] extends five degrees past the boundary, and the thresholds
     * match published guidance: Kp 6 for a chance in southern England, Kp 7 for a good one, Kp 5
     * for Scotland.
     */
    fun chance(kp: Double, geomagneticLatitude: Double): AuroraChance {
        val boundary = equatorwardBoundary(kp)
        val distanceIntoOval = abs(geomagneticLatitude) - boundary
        return when {
            distanceIntoOval >= OVERHEAD_MARGIN -> AuroraChance.OVERHEAD
            distanceIntoOval >= 0 -> AuroraChance.LIKELY
            distanceIntoOval >= POSSIBLE_MARGIN -> AuroraChance.POSSIBLE
            distanceIntoOval >= FAINT_MARGIN -> AuroraChance.FAINT_ON_HORIZON
            else -> AuroraChance.NONE
        }
    }

    /**
     * The lowest Kp at which this place has any real chance, or `null` if even Kp 9 would not do it.
     *
     * The single most useful number for somewhere that rarely sees aurora: "you need Kp 6 here" turns a
     * screen that says *no* most nights into one that says what to wait for. `null` for the tropics, where
     * the honest answer is never.
     */
    fun minimumKpForChance(geomagneticLatitude: Double): Int? = (KP_MINIMUM..KP_MAXIMUM).firstOrNull { kp ->
        chance(kp.toDouble(), geomagneticLatitude) >= AuroraChance.POSSIBLE
    }

    private fun radians(value: Double) = value * PI / HALF_TURN

    private fun degrees(value: Double) = value * HALF_TURN / PI

    /**
     * NOAA's equatorward auroral boundary, in geomagnetic latitude, indexed by whole Kp.
     *
     * Published values, not fitted ones, from NOAA SWPC's own aurora tutorial.
     */
    @Suppress("MagicNumber")
    private val EQUATORWARD_BOUNDARY_BY_KP = listOf(
        66.5, 64.5, 62.4, 60.4, 58.3, 56.3, 54.2, 52.2, 50.1, 48.1,
    )

    /** Kp runs 0 to 9; the boundary table has one entry per whole step. */
    private const val KP_MINIMUM = 0
    private const val KP_MAXIMUM = 9

    private const val HALF_TURN = 180.0

    // The geomagnetic north pole, IGRF epoch 2020.
    private const val POLE_LATITUDE = 80.65
    private const val POLE_LONGITUDE = -72.68

    /** Degrees inside the oval before the aurora is overhead rather than to the north. */
    private const val OVERHEAD_MARGIN = 5.0

    /** Aurora is high enough to be seen a degree equatorward of the oval's edge. */
    private const val POSSIBLE_MARGIN = -1.0

    /** And faintly, low on the horizon, for several degrees beyond that. */
    private const val FAINT_MARGIN = -5.0
}

/**
 * How likely the aurora is to be visible.
 *
 * Ordered, so a caller can ask whether the chance is at least something.
 */
enum class AuroraChance {
    /** Not from here, at this Kp. */
    NONE,

    /** A glow low on the poleward horizon, for a camera more than an eye. */
    FAINT_ON_HORIZON,

    /** Worth going outside and looking north. */
    POSSIBLE,

    /** The oval reaches this place: expect a display to the north. */
    LIKELY,

    /** Inside the oval, overhead, not on the horizon. */
    OVERHEAD,
}
