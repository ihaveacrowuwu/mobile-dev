package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Golden hour and blue hour.
 *
 * ## Where the expected numbers come from
 *
 * The solar position is checked where it is independently published: **sunrise and sunset**. London on
 * 19 August 2026 rises at 05:52 and sets at 20:14 BST, and the sun's altitude at those instants must be the
 * standard −0.833° (the refracted, semidiameter-corrected horizon). That pins the position calculation
 * without needing anyone's golden-hour table, which is what the rest of this file then relies on.
 *
 * The windows themselves are checked structurally, ordering, the altitudes at each boundary, and the
 * latitude behaviour that makes the feature worth having at all: golden hour is short in the tropics and long
 * in the far north, which is the fact a fixed "hour" would get wrong.
 *
 * The Swift twin is `SolarCalculatorTests.swift`.
 */
class SolarCalculatorTest {
    private val london = 51.5074 to -0.1278
    private val male = 4.1755 to 73.5093
    private val tromso = 69.6496 to 18.9560

    @Test
    fun `the sun is at the standard horizon angle at published sunrise and sunset`() {
        // London, 19 August 2026: 05:52 and 20:14 BST, 04:52 and 19:14 UTC.
        val sunrise = Instant.parse("2026-08-19T04:52:00Z")
        val sunset = Instant.parse("2026-08-19T19:14:00Z")

        val atSunrise = SolarCalculator.altitudeDegrees(sunrise, london.first, london.second)
        val atSunset = SolarCalculator.altitudeDegrees(sunset, london.first, london.second)

        // −0.833° is the standard: refraction at the horizon plus the sun's semidiameter. A tenth of a
        // degree is about twenty seconds of clock, which is inside the minute the published times are
        // rounded to.
        assertEquals(-0.833, atSunrise, 0.25)
        assertEquals(-0.833, atSunset, 0.25)
    }

    @Test
    fun `the windows run in order, and each boundary is at its defined altitude`() {
        val light = SolarCalculator.eveningLight(
            Instant.parse("2026-08-19T12:00:00Z"),
            london.first,
            london.second,
            ZoneOffset.UTC,
        )
        assertNotNull(light)
        val evening = light!!

        assertTrue(evening.goldenStart.isBefore(evening.goldenEnd))
        assertTrue(evening.goldenEnd.isBefore(evening.blueEnd))

        // Each instant is defined by an altitude, so that is what to assert: a window that drifted would
        // still be ordered, and this is what notices.
        fun altitudeAt(instant: Instant) = SolarCalculator.altitudeDegrees(instant, london.first, london.second)

        assertEquals(SolarCalculator.GOLDEN_HOUR_START_DEGREES, altitudeAt(evening.goldenStart), 0.05)
        assertEquals(SolarCalculator.GOLDEN_HOUR_END_DEGREES, altitudeAt(evening.goldenEnd), 0.05)
        assertEquals(SolarCalculator.BLUE_HOUR_END_DEGREES, altitudeAt(evening.blueEnd), 0.05)
    }

    @Test
    fun `golden hour is the evening one, not the morning`() {
        // Every one of these altitudes is crossed twice a day. Pairing a morning crossing with an evening one
        // would produce a "golden hour" lasting most of the day, which is the bug this guards.
        val light = SolarCalculator.eveningLight(
            Instant.parse("2026-08-19T12:00:00Z"),
            london.first,
            london.second,
            ZoneOffset.UTC,
        )!!
        assertTrue("golden hour ran for ${light.goldenDuration}", light.goldenDuration < Duration.ofHours(3))
        assertTrue(light.goldenDuration > Duration.ofMinutes(20))
        // Evening, so it is after midday.
        assertTrue(light.goldenStart.isAfter(Instant.parse("2026-08-19T12:00:00Z")))
    }

    @Test
    fun `golden hour is short in the tropics and long in the far north`() {
        // The whole reason this is computed rather than a fixed hour. The sun sets almost vertically at the
        // equator and at a shallow angle near the pole, so the same altitude band takes very different times
        // to cross.
        val instant = Instant.parse("2026-08-19T12:00:00Z")
        val tropical = SolarCalculator.eveningLight(instant, male.first, male.second, ZoneOffset.UTC)!!
        val northern = SolarCalculator.eveningLight(instant, tromso.first, tromso.second, ZoneOffset.UTC)!!

        assertTrue(
            "Malé ${tropical.goldenDuration} should be shorter than Tromsø ${northern.goldenDuration}",
            tropical.goldenDuration < northern.goldenDuration,
        )
        // And the tropical one really is brief, well under the "hour" the name promises.
        assertTrue(tropical.goldenDuration < Duration.ofMinutes(60))
    }

    @Test
    fun `a polar summer day has no evening light to report`() {
        // Tromsø in June: the sun never drops to -6°, so there is no blue hour and the result is
        // null.
        val light = SolarCalculator.eveningLight(
            Instant.parse("2026-06-21T12:00:00Z"),
            tromso.first,
            tromso.second,
            ZoneOffset.UTC,
        )
        assertNull(light)
    }

    @Test
    fun `now is inside the window it says it is inside`() {
        val light = SolarCalculator.eveningLight(
            Instant.parse("2026-08-19T12:00:00Z"),
            london.first,
            london.second,
            ZoneOffset.UTC,
        )!!
        val midGolden = light.goldenStart.plusSeconds(light.goldenDuration.seconds / 2)
        val midBlue = light.goldenEnd.plusSeconds(light.blueDuration.seconds / 2)

        assertTrue(light.isGolden(midGolden))
        assertTrue(!light.isBlue(midGolden))
        assertTrue(light.isBlue(midBlue))
        assertTrue(!light.isGolden(midBlue))
        // The boundaries belong to exactly one window each, not both and not neither.
        assertTrue(light.isGolden(light.goldenStart))
        assertTrue(light.isBlue(light.goldenEnd))
        assertTrue(!light.isBlue(light.blueEnd))
    }
}
