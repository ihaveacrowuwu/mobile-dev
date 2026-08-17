package com.nauhaan.skycast.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nauhaan.skycast.data.local.dao.SavedLocationDao
import com.nauhaan.skycast.data.local.dao.WeatherCacheDao
import com.nauhaan.skycast.data.local.entity.CachedForecastReadingEntity
import com.nauhaan.skycast.data.local.entity.CachedWeatherEntity
import com.nauhaan.skycast.data.local.entity.SavedLocationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cache-write behaviour, against real SQLite and real Room codegen.
 *
 * Guards the cache against becoming write-once. Room implements `@Upsert` as "INSERT, and on a
 * uniqueness conflict UPDATE … WHERE id = ?", so a surrogate `@PrimaryKey(autoGenerate = true) id`
 * beside a separate unique index on the natural key makes every write after the first match zero
 * rows and be discarded with no error.
 *
 * Instrumented rather than JVM, because the behaviour lives in generated SQL and a fake would
 * reproduce nothing.
 */
@RunWith(AndroidJUnit4::class)
class WeatherCacheDaoTest {
    private lateinit var database: SkyCastDatabase
    private lateinit var weatherDao: WeatherCacheDao
    private lateinit var locationDao: SavedLocationDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SkyCastDatabase::class.java,
            )
            .build()
        weatherDao = database.weatherCacheDao()
        locationDao = database.savedLocationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertWeatherReplacesTheExistingRow() = runTest {
        val locationId = insertLondon()

        weatherDao.upsertWeather(weather(locationId, temperature = 12.0, cachedAt = 1_000))
        weatherDao.upsertWeather(weather(locationId, temperature = 25.0, cachedAt = 2_000))

        val stored = weatherDao.getWeather(locationId)
        assertNotNull("a row must exist after two upserts", stored)
        // The assertion that actually breaks under the old schema. Asserting merely that *a* row
        // exists, the weaker, nearby property, was true either way, which is exactly how the bug
        // survived: the first insert always succeeded.
        assertEquals(25.0, stored!!.temperatureCelsius, 0.001)
        assertEquals(2_000L, stored.cachedAtEpochSeconds)
    }

    @Test
    fun upsertWeatherKeepsOneRowPerLocation() = runTest {
        val locationId = insertLondon()

        repeat(times = 5) { index ->
            weatherDao.upsertWeather(
                weather(locationId, temperature = index.toDouble(), cachedAt = index.toLong()),
            )
        }

        assertEquals(1, countRows("cached_weather"))
    }

    @Test
    fun upsertWeatherPersistsTheTimeZoneOffset() = runTest {
        val locationId = insertLondon()

        weatherDao.upsertWeather(
            weather(locationId, temperature = 20.0, cachedAt = 1_000, timeZoneOffsetSeconds = 3_600),
        )

        assertEquals(3_600, weatherDao.getWeather(locationId)?.timezoneOffsetSeconds)
    }

    @Test
    fun replaceForecastSwapsTheWholeSetRatherThanInterleaving() = runTest {
        val locationId = insertLondon()

        weatherDao.replaceForecast(locationId, listOf(reading(locationId, time = 100)))
        weatherDao.replaceForecast(
            locationId,
            listOf(reading(locationId, time = 200), reading(locationId, time = 300)),
        )

        // Yesterday's reading must be gone, not merged in beside today's.
        assertEquals(2, countRows("cached_forecast_readings"))
    }

    /**
     * Also confirms foreign-key enforcement is actually on: Room enables `PRAGMA foreign_keys`
     * itself, and this is the test that would notice if that changed.
     */
    @Test
    fun deletingALocationCascadesToItsCache() = runTest {
        val locationId = insertLondon()
        weatherDao.upsertWeather(weather(locationId, temperature = 20.0, cachedAt = 1_000))
        weatherDao.replaceForecast(locationId, listOf(reading(locationId, time = 100)))

        locationDao.delete(locationDao.getById(locationId)!!)

        assertEquals(0, countRows("cached_weather"))
        assertEquals(0, countRows("cached_forecast_readings"))
    }

    // MARK: - Helpers

    private suspend fun insertLondon(): Long = locationDao.insert(
        SavedLocationEntity(
            name = "London",
            countryCode = "GB",
            state = "England",
            latitude = 51.5074,
            longitude = -0.1278,
            isPrimary = true,
        ),
    )

    private fun countRows(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun weather(locationId: Long, temperature: Double, cachedAt: Long, timeZoneOffsetSeconds: Int = 0) =
        CachedWeatherEntity(
            locationId = locationId,
            locationName = "London",
            conditionId = 0,
            description = "Clear sky",
            iconCode = "01d",
            temperatureCelsius = temperature,
            feelsLikeCelsius = temperature,
            minTemperatureCelsius = temperature - 2,
            maxTemperatureCelsius = temperature + 2,
            humidityPercent = 60,
            pressureHpa = 1_013,
            windSpeedMetresPerSecond = 4.5,
            windDirectionDegrees = 220,
            cloudinessPercent = 5,
            visibilityMetres = 10_000,
            sunriseEpochSeconds = 1_000,
            sunsetEpochSeconds = 2_000,
            observedAtEpochSeconds = cachedAt,
            cachedAtEpochSeconds = cachedAt,
            timezoneOffsetSeconds = timeZoneOffsetSeconds,
        )

    private fun reading(locationId: Long, time: Long) = CachedForecastReadingEntity(
        locationId = locationId,
        locationName = "London",
        timeEpochSeconds = time,
        conditionId = 0,
        description = "Clear sky",
        iconCode = "01d",
        temperatureCelsius = 20.0,
        precipitationProbability = 0.0,
        windSpeedMetresPerSecond = 4.5,
        cachedAtEpochSeconds = time,
        timezoneOffsetSeconds = 3_600,
    )
}
