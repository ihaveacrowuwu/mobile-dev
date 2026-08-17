package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.local.entity.CachedForecastReadingEntity
import com.nauhaan.skycast.data.mapper.WeatherMapper.toDomain
import com.nauhaan.skycast.data.mapper.WeatherMapper.toDomainForecast
import com.nauhaan.skycast.data.mapper.WeatherMapper.toEntities
import com.nauhaan.skycast.data.mapper.WeatherMapper.toEntity
import com.nauhaan.skycast.data.remote.dto.CurrentWeatherDto
import com.nauhaan.skycast.data.remote.dto.ForecastResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Regression guards for sunrise and sunset being rendered in the **device's** timezone rather than
 * the location's, and for the forecast's day grouping depending on which path served the read.
 *
 * **The JVM default zone is forced to a non-UTC, non-London value in the initialiser.** A test
 * running in UTC cannot distinguish "the location's zone" from "the device's zone", because in UTC
 * the two agree.
 *
 * The matching assertion about *formatting* lives in `:app`'s `WeatherDetailsTest`, because that is
 * where the sunrise string is produced.
 */
class LocationTimeZoneTest {
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

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    init {
        // UTC+5 (Maldives), different from both UTC and the fixtures' UTC+1.
        TimeZone.setDefault(TimeZone.getTimeZone("Indian/Maldives"))
    }

    @Suppress("unused") // JUnit calls it; restoring the default keeps other suites unaffected.
    @org.junit.After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `current weather carries the location's UTC offset, not the device's`() {
        val dto = json.decodeFromString<CurrentWeatherDto>(fixture("current_weather_london.json"))

        val weather = dto.toDomain(locationId = 1, locationName = "London", cachedAt = Instant.EPOCH)

        // The fixture's "timezone" field is 3600, London on BST.
        assertEquals(ZoneOffset.ofHours(1), weather.zoneOffset)
    }

    @Test
    fun `offset survives the round trip through the cache`() {
        val dto = json.decodeFromString<CurrentWeatherDto>(fixture("current_weather_london.json"))
        val weather = dto.toDomain(locationId = 1, locationName = "London", cachedAt = Instant.EPOCH)

        val restored = weather.toEntity().toDomain()

        assertEquals(weather.zoneOffset, restored.zoneOffset)
    }

    @Test
    fun `forecast day grouping is identical whether read from the API or from the cache`() {
        val dto = json.decodeFromString<ForecastResponseDto>(fixture("forecast_london.json"))
        val fromApi = dto.toDomain(
            locationId = 1,
            locationName = "London",
            cachedAt = Instant.parse("2026-08-04T12:00:00Z"),
        )

        val fromCache = fromApi.toEntities().toDomainForecast()

        assertNotNull("cached rows must rebuild into a forecast", fromCache)
        // The dates are the identity of a day-detail route. If these two lists differ, tapping a
        // day while online and reopening it offline lands on "day not available".
        assertEquals(fromApi.days.map { it.date }, fromCache!!.days.map { it.date })
        assertEquals(fromApi.zoneOffset, fromCache.zoneOffset)
    }

    @Test
    fun `a corrupted cache offset falls back to UTC instead of throwing`() {
        // ZoneOffset.ofTotalSeconds rejects anything beyond ±18 hours.
        val nonsense = 999_999

        val forecast = listOf(sampleReading(timezoneOffsetSeconds = nonsense)).toDomainForecast()

        assertNotNull(forecast)
        assertEquals(ZoneOffset.UTC, forecast!!.zoneOffset)
    }

    private fun sampleReading(timezoneOffsetSeconds: Int) = CachedForecastReadingEntity(
        locationId = 1,
        locationName = "London",
        timeEpochSeconds = Instant.parse("2026-08-04T12:00:00Z").epochSecond,
        conditionId = 0,
        description = "Clear sky",
        iconCode = "01d",
        temperatureCelsius = 22.0,
        precipitationProbability = 0.0,
        windSpeedMetresPerSecond = 4.5,
        cachedAtEpochSeconds = Instant.parse("2026-08-04T12:00:00Z").epochSecond,
        timezoneOffsetSeconds = timezoneOffsetSeconds,
    )
}
