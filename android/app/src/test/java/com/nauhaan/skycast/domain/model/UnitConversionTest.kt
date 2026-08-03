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
    fun `every unit exposes a non-empty symbol for display`() {
        // Guards against adding an enum case and forgetting its label, which would
        // render as an empty string next to the temperature.
        TemperatureUnit.entries.forEach { assert(it.symbol.isNotBlank()) }
        WindSpeedUnit.entries.forEach { assert(it.symbol.isNotBlank()) }
    }

    private companion object {
        const val DELTA = 0.0001
        const val LOOSE_DELTA = 0.01
    }
}
