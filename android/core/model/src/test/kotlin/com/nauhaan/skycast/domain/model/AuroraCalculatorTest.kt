package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Whether the aurora is worth going outside for.
 *
 * ## Where the expected numbers come from
 *
 * Two independent sources, neither of them this code:
 *
 * 1. **Published geomagnetic latitudes** for well-known places, London ~54°, Tromsø ~67°, Anchorage ~61°.
 *    That checks the coordinate transform on its own.
 * 2. **Published aurora-watching guidance**, which is unusually specific and widely agreed: Kp 5 puts aurora
 *    over Scotland, Kp 6 gives southern England a chance, Kp 7 makes it likely there, and Tromsø sees it at
 *    almost any Kp because it sits under the oval. Those are the assertions that matter, because they test
 *    the boundary table, the visibility margins and the transform *together* against reality.
 *
 * The Swift twin is `AuroraCalculatorTests.swift`, asserting the same values.
 */
class AuroraCalculatorTest {
    private val london = 51.5074 to -0.1278
    private val edinburgh = 55.9533 to -3.1883
    private val tromso = 69.6496 to 18.9560
    private val anchorage = 61.2181 to -149.9003
    private val male = 4.1755 to 73.5093

    // ── The coordinate transform ──────────────────────────────────────────

    @Test
    fun `geomagnetic latitude matches published values`() {
        // Checked alone, because everything else depends on it. Geographic latitude does not work
        // here: London and Calgary are at nearly the same latitude on a map, and one of them sees
        // aurora most years.
        assertEquals(54.0, AuroraCalculator.geomagneticLatitude(london.first, london.second), 1.5)
        assertEquals(67.0, AuroraCalculator.geomagneticLatitude(tromso.first, tromso.second), 1.5)
        assertEquals(61.0, AuroraCalculator.geomagneticLatitude(anchorage.first, anchorage.second), 1.5)
    }

    @Test
    fun `the tropics are nowhere near the oval`() {
        val magnetic = AuroraCalculator.geomagneticLatitude(male.first, male.second)
        assertTrue("Malé came out at $magnetic", kotlin.math.abs(magnetic) < 10)
    }

    // ── The boundary table ────────────────────────────────────────────────

    @Test
    fun `the oval reaches further from the pole as the field is disturbed`() {
        // NOAA's published endpoints, and the direction between them. A sign error here would put the aurora
        // over the equator during a storm.
        assertEquals(66.5, AuroraCalculator.equatorwardBoundary(0.0), 0.01)
        assertEquals(48.1, AuroraCalculator.equatorwardBoundary(9.0), 0.01)
        assertTrue(AuroraCalculator.equatorwardBoundary(3.0) > AuroraCalculator.equatorwardBoundary(6.0))
    }

    @Test
    fun `a fractional Kp interpolates rather than rounding`() {
        // The reported index is not an integer, 4.67 is a real value NOAA publishes, and rounding it away
        // moves the boundary by about a degree, which is a whole band of this screen's answers.
        val low = AuroraCalculator.equatorwardBoundary(4.0)
        val high = AuroraCalculator.equatorwardBoundary(5.0)
        val middle = AuroraCalculator.equatorwardBoundary(4.5)
        assertEquals((low + high) / 2, middle, 0.01)
    }

    @Test
    fun `Kp outside 0 to 9 is clamped rather than extrapolated`() {
        assertEquals(AuroraCalculator.equatorwardBoundary(0.0), AuroraCalculator.equatorwardBoundary(-2.0), 0.01)
        assertEquals(AuroraCalculator.equatorwardBoundary(9.0), AuroraCalculator.equatorwardBoundary(12.0), 0.01)
    }

    // ── The answer, against published guidance ────────────────────────────

    @Test
    fun `Tromso sees aurora at almost any Kp`() {
        // It sits under the oval; that is why people go there. A model that needed a storm for Tromsø would
        // be wrong about the one place everybody knows the answer for.
        val magnetic = AuroraCalculator.geomagneticLatitude(tromso.first, tromso.second)
        assertTrue(AuroraCalculator.chance(0.0, magnetic) >= AuroraChance.LIKELY)
        assertTrue(AuroraCalculator.chance(3.0, magnetic) >= AuroraChance.OVERHEAD)
    }

    @Test
    fun `southern England needs a storm, Scotland needs less`() {
        // The published guidance this whole model is judged against: Kp 6 gives London a chance and Kp 7
        // makes it likely, while Scotland manages at Kp 5.
        val londonMagnetic = AuroraCalculator.geomagneticLatitude(london.first, london.second)
        val edinburghMagnetic = AuroraCalculator.geomagneticLatitude(edinburgh.first, edinburgh.second)

        assertTrue(AuroraCalculator.chance(4.0, londonMagnetic) < AuroraChance.POSSIBLE)
        assertTrue(AuroraCalculator.chance(6.0, londonMagnetic) >= AuroraChance.POSSIBLE)
        assertTrue(AuroraCalculator.chance(7.0, londonMagnetic) >= AuroraChance.LIKELY)
        assertTrue(AuroraCalculator.chance(5.0, edinburghMagnetic) >= AuroraChance.POSSIBLE)
    }

    @Test
    fun `the minimum Kp for a place matches what aurora watchers quote`() {
        // "You need Kp 6 in southern England" is the single most repeated number in this hobby.
        val londonMagnetic = AuroraCalculator.geomagneticLatitude(london.first, london.second)
        assertEquals(6, AuroraCalculator.minimumKpForChance(londonMagnetic))

        // And Tromsø needs nothing at all.
        val tromsoMagnetic = AuroraCalculator.geomagneticLatitude(tromso.first, tromso.second)
        assertEquals(0, AuroraCalculator.minimumKpForChance(tromsoMagnetic))
    }

    @Test
    fun `the tropics never see it, even at Kp 9`() {
        // The honest answer, and the one a "chance" model most easily gets wrong by scaling smoothly.
        val magnetic = AuroraCalculator.geomagneticLatitude(male.first, male.second)
        assertEquals(AuroraChance.NONE, AuroraCalculator.chance(9.0, magnetic))
        assertNull(AuroraCalculator.minimumKpForChance(magnetic))
    }

    @Test
    fun `the southern hemisphere is handled, if less precisely`() {
        // Kp says nothing about which pole, and the aurora australis is the same phenomenon, so a
        // model using signed latitude rather than distance from the equator would answer "never"
        // for all of it.
        //
        // Asserted loosely: the centred-dipole transform is good in the north and rough in the
        // south, where the real field is least dipole-like. This checks the two things the
        // approximation supports: the sign is right, and a great storm registers as something.
        val magnetic = AuroraCalculator.geomagneticLatitude(-46.4, 168.35)
        assertTrue("Invercargill came out at $magnetic", magnetic < 0)
        assertTrue(AuroraCalculator.chance(9.0, magnetic) >= AuroraChance.FAINT_ON_HORIZON)
    }

    // ── The forecast wrapper ──────────────────────────────────────────────

    @Test
    fun `the peak ahead is the highest Kp inside the window, not the whole feed`() {
        val now = Instant.parse("2026-08-19T00:00:00Z")
        val weather = SpaceWeather(
            kpNow = 3.0,
            observedAt = now,
            stormLevel = null,
            upcoming = listOf(
                KpPeriod(now.plus(Duration.ofHours(3)), 4.0, null),
                KpPeriod(now.plus(Duration.ofHours(9)), 6.0, "G2"),
                // Beyond the window: a storm three days out must not be reported as tonight's peak.
                KpPeriod(now.plus(Duration.ofHours(60)), 8.0, "G4"),
            ),
            cachedAt = now,
        )

        val peak = weather.peakAhead()
        assertEquals(6.0, peak!!.kp, 0.001)
        assertEquals("G2", peak.stormLevel)
    }

    @Test
    fun `staleness follows the TTL`() {
        val now = Instant.parse("2026-08-19T00:00:00Z")
        val weather = SpaceWeather(3.0, now, null, emptyList(), cachedAt = now)
        assertTrue(!weather.isStale(now.plus(Duration.ofMinutes(29))))
        assertTrue(weather.isStale(now.plus(Duration.ofMinutes(31))))
    }
}
