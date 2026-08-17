package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit conversions.
 *
 * These matter more than their simplicity suggests: because everything is cached in
 * Celsius and m/s and converted at render time, a bug here would show wrong numbers on
 * every screen, including offline, where there is no network response to blame.
 */
class UnitConversionTest {
    @Test
    fun `celsius to celsius is identity`() {
        assertEquals(22.0, TemperatureUnit.CELSIUS.convertFromCelsius(22.0), DELTA)
    }

    @Test
    fun `celsius to fahrenheit uses the known reference points`() {
        assertEquals(32.0, TemperatureUnit.FAHRENHEIT.convertFromCelsius(0.0), DELTA)
        assertEquals(212.0, TemperatureUnit.FAHRENHEIT.convertFromCelsius(100.0), DELTA)
        // -40 is the crossover point where both scales agree, a good sanity check.
        assertEquals(-40.0, TemperatureUnit.FAHRENHEIT.convertFromCelsius(-40.0), DELTA)
    }

    @Test
    fun `wind speed conversions`() {
        val tenMetresPerSecond = 10.0
        assertEquals(
            10.0,
            WindSpeedUnit.METRES_PER_SECOND.convertFromMetresPerSecond(tenMetresPerSecond),
            DELTA,
        )
        assertEquals(
            36.0,
            WindSpeedUnit.KILOMETRES_PER_HOUR.convertFromMetresPerSecond(tenMetresPerSecond),
            DELTA,
        )
        assertEquals(
            22.369,
            WindSpeedUnit.MILES_PER_HOUR.convertFromMetresPerSecond(tenMetresPerSecond),
            LOOSE_DELTA,
        )
    }

    @Test
    fun `celsius to kelvin uses absolute zero`() {
        assertEquals(273.15, TemperatureUnit.KELVIN.convertFromCelsius(0.0), DELTA)
        // Absolute zero. Not a temperature London will reach, but the definition the scale rests on.
        assertEquals(0.0, TemperatureUnit.KELVIN.convertFromCelsius(-273.15), DELTA)
    }

    @Test
    fun `knots follow from the definition of the nautical mile`() {
        // A nautical mile is exactly 1852 m, so 1 kt is 1852 m/h and 1 m/s is 3600/1852 kt.
        assertEquals(1.943_844, WindSpeedUnit.KNOTS.convertFromMetresPerSecond(1.0), LOOSE_DELTA)
        // 10 m/s is a fresh breeze, about 19 kt, a number any sailor would recognise.
        assertEquals(19.438, WindSpeedUnit.KNOTS.convertFromMetresPerSecond(10.0), LOOSE_DELTA)
    }

    @Test
    fun `beaufort force follows the standard scale boundaries`() {
        // Each pair is (m/s, expected force) taken from the published scale.
        val cases = listOf(
            0.0 to 0, // calm
            1.0 to 1, // light air
            3.0 to 2, // light breeze
            5.0 to 3, // gentle breeze
            7.0 to 4, // moderate breeze
            10.0 to 5, // fresh breeze
            12.0 to 6, // strong breeze
            16.0 to 7, // near gale
            19.0 to 8, // gale
            23.0 to 9, // strong gale
            26.0 to 10, // storm
            30.0 to 11, // violent storm
            40.0 to 12, // hurricane force, no upper bound, so anything above 32.6 lands here
        )
        cases.forEach { (metresPerSecond, expectedForce) ->
            assertEquals(
                "wrong Beaufort force for $metresPerSecond m/s",
                expectedForce.toDouble(),
                WindSpeedUnit.BEAUFORT.convertFromMetresPerSecond(metresPerSecond),
                DELTA,
            )
        }
    }

    @Test
    fun `pressure conversions match the aviation reference values`() {
        // The ICAO standard atmosphere: 1013.25 hPa is 29.92 inHg, the altimeter setting every
        // pilot knows by heart. If this drifts, the number looks subtly wrong to the one kind of
        // user most likely to check it.
        assertEquals(
            29.92,
            PressureUnit.INCHES_OF_MERCURY.convertFromHectopascals(1_013.25),
            LOOSE_DELTA,
        )
        assertEquals(
            760.0,
            PressureUnit.MILLIMETRES_OF_MERCURY.convertFromHectopascals(1_013.25),
            0.1,
        )
        assertEquals(1_013.25, PressureUnit.HECTOPASCALS.convertFromHectopascals(1_013.25), DELTA)
    }

    @Test
    fun `visibility conversions use the exact nautical mile`() {
        assertEquals(10.0, VisibilityUnit.KILOMETRES.convertFromMetres(10_000.0), DELTA)
        assertEquals(6.214, VisibilityUnit.MILES.convertFromMetres(10_000.0), LOOSE_DELTA)
        // 1852 m is one nautical mile by definition, so this must be exactly 1.
        assertEquals(1.0, VisibilityUnit.NAUTICAL_MILES.convertFromMetres(1_852.0), DELTA)
    }

    @Test
    fun `every unit exposes a non-empty symbol and display name`() {
        // Guards against adding an enum case and forgetting its label, which would render as an
        // empty string next to the value, or as a blank row in Settings.
        val units: List<DisplayUnit> =
            TemperatureUnit.entries + WindSpeedUnit.entries + PressureUnit.entries + VisibilityUnit.entries
        units.forEach {
            assert(it.symbol.isNotBlank()) { "$it has no symbol" }
            assert(it.displayName.isNotBlank()) { "$it has no display name" }
        }
    }

    private companion object {
        const val DELTA = 0.0001
        const val LOOSE_DELTA = 0.01
    }
}
