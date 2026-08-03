package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The OpenWeather condition-id mapping.
 *
 * Boundary values are tested explicitly because the ranges in
 * [WeatherCondition.fromOpenWeatherId] are hand-written from the API documentation and
 * an off-by-one there would silently show the wrong artwork rather than crash.
 */
class WeatherConditionTest {
    @Test
    fun `maps thunderstorm range`() {
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromOpenWeatherId(200))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromOpenWeatherId(232))
    }

    @Test
    fun `maps drizzle range`() {
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromOpenWeatherId(300))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromOpenWeatherId(321))
    }

    @Test
    fun `maps rain range`() {
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromOpenWeatherId(500))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromOpenWeatherId(531))
    }

    @Test
    fun `maps snow range`() {
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromOpenWeatherId(600))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromOpenWeatherId(622))
    }

    @Test
    fun `maps atmosphere range to mist`() {
        assertEquals(WeatherCondition.MIST, WeatherCondition.fromOpenWeatherId(701))
        assertEquals(WeatherCondition.MIST, WeatherCondition.fromOpenWeatherId(781))
    }

    @Test
    fun `800 is clear and 801 upward is cloudy`() {
        // 800 sits alone between the atmosphere range and the cloud range, which makes
        // it the easiest boundary to get wrong.
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromOpenWeatherId(800))
        assertEquals(WeatherCondition.CLOUDS, WeatherCondition.fromOpenWeatherId(801))
        assertEquals(WeatherCondition.CLOUDS, WeatherCondition.fromOpenWeatherId(804))
    }

    @Test
    fun `unrecognised ids fall back to UNKNOWN rather than throwing`() {
        // A future API addition must degrade to generic artwork, never crash the app.
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromOpenWeatherId(0))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromOpenWeatherId(999))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromOpenWeatherId(-1))
    }
}
