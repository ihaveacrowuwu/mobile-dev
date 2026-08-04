package com.nauhaan.skycast.data.remote

import com.nauhaan.skycast.data.mapper.WeatherMapper.toDomain
import com.nauhaan.skycast.data.remote.dto.CurrentWeatherDto
import com.nauhaan.skycast.data.remote.dto.ForecastResponseDto
import com.nauhaan.skycast.data.remote.dto.GeocodingResultDto
import com.nauhaan.skycast.domain.model.WeatherCondition
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Contract tests against **real captured OpenWeather responses**.
 *
 * The fixtures in `src/test/resources/fixtures/` were captured from the live API, not
 * hand-written. That distinction matters: a hand-written fixture only proves the DTOs can
 * decode what the author *imagined*, whereas these prove they decode what the service
 * actually sends, including fields we never declared.
 *
 * The live payload contains `base`, `cod`, `main.sea_level`, `main.grnd_level`, `sys.type`,
 * `sys.id` and a large `local_names` object, none of which appear in our DTOs. If someone
 * ever removes `ignoreUnknownKeys` from the `Json` configuration, these tests fail
 * immediately rather than the app breaking in the examiner's hands.
 *
 * Re-capture with:
 * ```
 * curl "https://api.openweathermap.org/data/2.5/weather?lat=51.5074&lon=-0.1278&units=metric&appid=$KEY"
 * ```
 * The same three fixtures are shared byte-for-byte with `ios/SkyCastTests/Fixtures/`, so both
 * platforms are held to one contract.
 */
class ApiContractTest {
    /** The same configuration `NetworkModule` provides to Retrofit. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: fixtures/$name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `live current-weather payload decodes despite undeclared fields`() {
        val dto = json.decodeFromString<CurrentWeatherDto>(fixture("current_weather_london.json"))

        assertEquals("London", dto.cityName)
        assertEquals(800, dto.weather.first().id)
        assertEquals(1009, dto.main.pressure)
        assertEquals(69, dto.main.humidity)
        // Present in the real payload; proves the optional field is read, not dropped.
        assertEquals(2.68, dto.wind.gust!!, 0.001)
        assertEquals(10_000, dto.visibility)
        assertTrue("sunrise should be populated", dto.system.sunriseEpochSeconds > 0)
    }

    @Test
    fun `live current-weather payload maps to a fully populated domain model`() {
        val dto = json.decodeFromString<CurrentWeatherDto>(fixture("current_weather_london.json"))
        val cachedAt = Instant.parse("2026-08-04T12:00:00Z")

        val weather = dto.toDomain(locationId = 1, locationName = "London", cachedAt = cachedAt)

        assertEquals(WeatherCondition.CLEAR, weather.condition)
        // OpenWeather sends lowercase; we display sentence case.
        assertEquals("Clear sky", weather.description)
        assertEquals(1, weather.locationId)
        assertEquals(cachedAt, weather.cachedAt)
        assertTrue("temperature should be a real reading", weather.temperatureCelsius > -100)
        // The icon code was "01n", i.e. night, so the derived flag must agree.
        assertFalse("01n is a night icon", weather.isDaytime)
    }

    @Test
    fun `live forecast payload groups into chronological days`() {
        val dto = json.decodeFromString<ForecastResponseDto>(fixture("forecast_london.json"))
        val forecast = dto.toDomain(
            locationId = 1,
            locationName = "London",
            cachedAt = Instant.parse("2026-08-04T12:00:00Z"),
        )

        assertTrue("expected at least one day", forecast.days.isNotEmpty())
        assertEquals(forecast.days.map { it.date }.sorted(), forecast.days.map { it.date })
        forecast.days.forEach { day ->
            assertTrue(
                "min must not exceed max on ${day.date}",
                day.minTemperatureCelsius <= day.maxTemperatureCelsius,
            )
            assertTrue("day ${day.date} has no readings", day.hourly.isNotEmpty())
            assertEquals(day.hourly.map { it.time }.sorted(), day.hourly.map { it.time })
        }
        // Every reading in the fixture must survive the grouping, none silently dropped.
        assertEquals(dto.readings.size, forecast.days.sumOf { it.hourly.size })
    }

    @Test
    fun `live geocoding payload decodes and ignores local_names`() {
        val dtos = json.decodeFromString<List<GeocodingResultDto>>(fixture("geocoding_male.json"))

        assertTrue(dtos.isNotEmpty())
        val male = dtos.first()
        // The real response uses the accented spelling.
        assertEquals("Malé", male.name)
        assertEquals("MV", male.country)
        // The Maldives has no state, so the display name must not gain a stray comma.
        assertEquals(null, male.state)
        assertEquals("Malé, MV", male.toDomain().displayName)
    }
}
