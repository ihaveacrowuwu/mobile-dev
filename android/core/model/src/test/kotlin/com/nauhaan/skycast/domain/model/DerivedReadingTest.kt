package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The readings SkyCast derives rather than fetches.
 *
 * Mirrors `DerivedReadingTests` on iOS, including the reference values, a dew point that differed
 * between the platforms would be a defect no screenshot comparison would catch, because both would
 * look perfectly plausible.
 */
class DerivedReadingTest {
    @Test
    fun `saturated air has a dew point equal to its temperature`() {
        // The invariant that makes the formula trustworthy: air that is already saturated is at
        // its dew point by definition, so the two must agree exactly rather than approximately.
        listOf(-5.0, 0.0, 12.5, 30.0).forEach { temperature ->
            val weather = weather(temperature = temperature, humidity = 100)

            assertEquals(temperature, weather.dewPointCelsius, 0.01)
        }
    }

    @Test
    fun `dew point matches published values`() {
        // A tenth of a degree: the Magnus approximation is quoted to about that over this range,
        // and the app only ever displays whole degrees.
        assertEquals(9.3, weather(20.0, 50).dewPointCelsius, 0.1)
        assertEquals(26.0, weather(30.0, 79).dewPointCelsius, 0.1)
        assertEquals(1.4, weather(5.0, 78).dewPointCelsius, 0.1)
    }

    @Test
    fun `dew point never exceeds the temperature`() {
        // Physically impossible, and a UI that showed it would look broken. The tile draws its bar
        // as a fraction of the temperature, so this is also what keeps that fraction in its track.
        listOf(1, 25, 50, 75, 99, 100).forEach { humidity ->
            assertTrue(weather(18.0, humidity).dewPointCelsius <= 18.001)
        }
    }

    @Test
    fun `daylight is the span between sunrise and sunset`() {
        val weather = weather().copy(
            sunrise = TestInstant,
            sunset = TestInstant.plus(Duration.ofHours(14)).plus(Duration.ofMinutes(28)),
        )

        assertEquals(Duration.ofMinutes(14 * 60 + 28), weather.daylightDuration)
    }

    @Test
    fun `a sunset before sunrise reports no daylight`() {
        // Polar winter, or simply a malformed payload: either way a negative span would render as
        // a nonsense duration rather than "no daylight".
        val weather = weather().copy(
            sunrise = TestInstant,
            sunset = TestInstant.minus(Duration.ofHours(1)),
        )

        assertEquals(Duration.ZERO, weather.daylightDuration)
    }

    private fun weather(temperature: Double = 20.0, humidity: Int = 50) = Weather(
        locationId = 1,
        locationName = "London",
        condition = WeatherCondition.CLEAR,
        description = "Clear sky",
        iconCode = "01d",
        temperatureCelsius = temperature,
        feelsLikeCelsius = temperature,
        minTemperatureCelsius = temperature - 4,
        maxTemperatureCelsius = temperature + 3,
        humidityPercent = humidity,
        pressureHpa = 1013,
        windSpeedMetresPerSecond = 4.5,
        windDirectionDegrees = 220,
        cloudinessPercent = 5,
        visibilityMetres = 10_000,
        sunrise = TestInstant,
        sunset = TestInstant.plus(Duration.ofHours(12)),
        observedAt = TestInstant,
        zoneOffset = ZoneOffset.UTC,
        cachedAt = TestInstant,
    )

    private companion object {
        val TestInstant: Instant = Instant.parse("2026-08-04T12:00:00Z")
    }
}
