package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * The values the METAR screen derives rather than reads.
 *
 * A METAR reports temperature, dew point, altimeter and cloud layers; it never reports humidity, fog risk,
 * density altitude or the ceiling. Those are the four figures a pilot works out, and computing them is what
 * makes the screen worth more than a decoded table.
 *
 * ## Where the expected numbers come from
 *
 * Not from this code. Density altitude is checked against the worked example every ground-school text
 * uses, a 5000 ft field on a standard-pressure day at 25 °C gives 7400 ft, and against the definition
 * itself, which is that a standard day at sea level is zero. Humidity is checked against published
 * psychrometric tables: 26/14 is about 48%, 20/5 about 38%.
 *
 * The Swift twin is `MetarDerivationsTests.swift`, asserting the same values.
 */
class MetarDerivationsTest {
    // ── Ceiling ───────────────────────────────────────────────────────────

    @Test
    fun `only broken and overcast layers count as a ceiling`() {
        // The assertion that matters, and the one an implementation gets wrong by taking the lowest cloud:
        // you can climb through gaps in scattered cloud under visual rules, which is why the flight-category
        // thresholds are defined against BKN and OVC alone. FEW at 1200 ft is not a 1200 ft ceiling.
        val report = report(
            clouds = listOf(
                CloudLayer(cover = "FEW", baseFeet = 1_200),
                CloudLayer(cover = "SCT", baseFeet = 2_500),
                CloudLayer(cover = "BKN", baseFeet = 4_800),
                CloudLayer(cover = "OVC", baseFeet = 7_000),
            ),
        )
        assertEquals(4_800, report.ceilingFeet)
    }

    @Test
    fun `a sky with no broken or overcast layer has no ceiling`() {
        val report = report(clouds = listOf(CloudLayer(cover = "SCT", baseFeet = 4_800)))
        assertNull(report.ceilingFeet)
        // NCD, nothing detected, decodes to no layers at all, which is also no ceiling rather than zero.
        assertNull(report(clouds = emptyList()).ceilingFeet)
    }

    // ── Humidity and the spread ───────────────────────────────────────────

    @Test
    fun `relative humidity matches the psychrometric tables`() {
        // Published tables: 26 °C over a 14 °C dew point is about 48%, and 20 over 5 about 38%.
        assertEquals(48, report(temperature = 26.0, dewPoint = 14.0).relativeHumidityPercent)
        assertEquals(37, report(temperature = 20.0, dewPoint = 5.0).relativeHumidityPercent)
    }

    @Test
    fun `saturated air is a hundred percent`() {
        // The definition: dew point equal to temperature *is* saturation. A formula that returned 99 or 101
        // here would be visibly wrong on a foggy morning, which is exactly when someone reads this screen.
        assertEquals(100, report(temperature = 14.0, dewPoint = 14.0).relativeHumidityPercent)
        assertEquals(100, report(temperature = -3.0, dewPoint = -3.0).relativeHumidityPercent)
    }

    @Test
    fun `the dew point spread is the gap between the two`() {
        assertEquals(12.0, report(temperature = 26.0, dewPoint = 14.0).dewPointSpreadCelsius!!, 0.001)
        assertEquals(0.0, report(temperature = 14.0, dewPoint = 14.0).dewPointSpreadCelsius!!, 0.001)
    }

    @Test
    fun `derived values are absent when their inputs are`() {
        // A METAR can omit temperature and dew point entirely; the screen must show nothing rather than a
        // confident zero.
        val bare = report(temperature = null, dewPoint = null, altimeter = null)
        assertNull(bare.relativeHumidityPercent)
        assertNull(bare.dewPointSpreadCelsius)
        assertNull(bare.densityAltitudeFeet)
    }

    // ── Density altitude ──────────────────────────────────────────────────

    @Test
    fun `density altitude matches the ground-school worked example`() {
        // 5000 ft field, standard pressure, 25 °C. ISA at 5000 ft is 5 °C, so the air is 20 °C warm and the
        // field performs like 5000 + 20 x 120 = 7400 ft.
        val report = report(
            elevationMetres = (5_000 / 3.28084).toInt(),
            temperature = 25.0,
            altimeter = 1_013.25,
        )
        assertEquals(7_400.0, report.densityAltitudeFeet!!.toDouble(), 15.0)
    }

    @Test
    fun `a standard day at sea level is zero`() {
        // The definition of the standard atmosphere, and a check no worked example can fake: sea level,
        // 1013.25 hPa, 15 °C.
        val report = report(elevationMetres = 0, temperature = 15.0, altimeter = 1_013.25)
        assertEquals(0.0, report.densityAltitudeFeet!!.toDouble(), 1.0)
    }

    @Test
    fun `hotter and lower-pressure air raises the density altitude`() {
        val cool = report(elevationMetres = 10, temperature = 5.0, altimeter = 1_013.25)
        val warm = report(elevationMetres = 10, temperature = 30.0, altimeter = 1_013.25)
        val low = report(elevationMetres = 10, temperature = 5.0, altimeter = 990.0)

        // The two directions that make the figure worth showing at all.
        assertEquals(true, warm.densityAltitudeFeet!! > cool.densityAltitudeFeet!!)
        assertEquals(true, low.densityAltitudeFeet!! > cool.densityAltitudeFeet!!)
    }

    private fun report(
        elevationMetres: Int = 10,
        temperature: Double? = 26.0,
        dewPoint: Double? = 14.0,
        altimeter: Double? = 1_009.0,
        clouds: List<CloudLayer> = emptyList(),
    ) = MetarReport(
        stationId = "EGLC",
        stationName = "London City",
        distanceKm = 12.7,
        latitude = 51.5,
        longitude = 0.05,
        elevationMetres = elevationMetres,
        observedAt = Instant.EPOCH,
        temperatureCelsius = temperature,
        dewPointCelsius = dewPoint,
        windDirectionDegrees = 250,
        windSpeedKnots = 11,
        visibilityStatuteMiles = 6.0,
        visibilityIsOrGreater = true,
        altimeterHectopascals = altimeter,
        clouds = clouds,
        flightCategory = FlightCategory.VFR,
        raw = "METAR EGLC 181450Z AUTO 25011KT 9999 NCD 26/14 Q1009",
        cachedAt = Instant.EPOCH,
    )
}
