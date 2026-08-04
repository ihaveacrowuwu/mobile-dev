package com.nauhaan.skycast.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nauhaan.skycast.data.local.entity.CachedForecastReadingEntity
import com.nauhaan.skycast.data.local.entity.CachedWeatherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherCacheDao {
    // ── Current conditions ─────────────────────────────────────────────────

    @Query("SELECT * FROM cached_weather WHERE location_id = :locationId LIMIT 1")
    fun observeWeather(locationId: Long): Flow<CachedWeatherEntity?>

    @Query("SELECT * FROM cached_weather WHERE location_id = :locationId LIMIT 1")
    suspend fun getWeather(locationId: Long): CachedWeatherEntity?

    @Upsert
    suspend fun upsertWeather(weather: CachedWeatherEntity)

    // ── Forecast ───────────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM cached_forecast_readings
        WHERE location_id = :locationId
        ORDER BY time_epoch ASC
        """,
    )
    fun observeForecast(locationId: Long): Flow<List<CachedForecastReadingEntity>>

    @Upsert
    suspend fun upsertForecastReadings(readings: List<CachedForecastReadingEntity>)

    @Query("DELETE FROM cached_forecast_readings WHERE location_id = :locationId")
    suspend fun deleteForecast(locationId: Long)

    /**
     * Replaces a location's forecast wholesale.
     *
     * Transactional because a partial replace would leave yesterday's readings
     * interleaved with today's, the forecast list would silently show stale days.
     */
    @Transaction
    suspend fun replaceForecast(locationId: Long, readings: List<CachedForecastReadingEntity>) {
        deleteForecast(locationId)
        upsertForecastReadings(readings)
    }

    // ── Maintenance ────────────────────────────────────────────────────────

    /** Drops readings older than [cutoffEpochSeconds]. Run on app start. */
    @Query("DELETE FROM cached_forecast_readings WHERE time_epoch < :cutoffEpochSeconds")
    suspend fun pruneExpiredForecastReadings(cutoffEpochSeconds: Long)

    @Transaction
    suspend fun clearAll() {
        deleteAllWeather()
        deleteAllForecastReadings()
    }

    @Query("DELETE FROM cached_weather")
    suspend fun deleteAllWeather()

    @Query("DELETE FROM cached_forecast_readings")
    suspend fun deleteAllForecastReadings()
}
