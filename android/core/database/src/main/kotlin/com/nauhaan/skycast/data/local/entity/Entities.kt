package com.nauhaan.skycast.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities. Like DTOs, these never leave the data layer, `EntityMapper`
 * converts them to domain models at the repository boundary.
 *
 * Room schemas are exported to `app/schemas/` (configured in `build.gradle.kts`) and
 * committed, so any schema change shows up as a reviewable diff and a missing
 * migration is caught in review rather than by a crash on a user's device.
 */

/** A place the user chose to track. Durable, user-owned data, never cache-evicted. */
@Entity(
    tableName = "saved_locations",
    indices = [Index(value = ["latitude", "longitude"], unique = true)],
)
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "country_code") val countryCode: String,
    val state: String? = null,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean = false,
)

/**
 * Cached current conditions, one row per saved location.
 *
 * `onDelete = CASCADE` means removing a location automatically drops its cache,
 * no orphan rows and no manual cleanup code to forget.
 */
@Entity(
    tableName = "cached_weather",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["location_id"], unique = true)],
)
data class CachedWeatherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "condition_id") val conditionId: Int,
    val description: String,
    @ColumnInfo(name = "icon_code") val iconCode: String,
    @ColumnInfo(name = "temperature_celsius") val temperatureCelsius: Double,
    @ColumnInfo(name = "feels_like_celsius") val feelsLikeCelsius: Double,
    @ColumnInfo(name = "min_temperature_celsius") val minTemperatureCelsius: Double,
    @ColumnInfo(name = "max_temperature_celsius") val maxTemperatureCelsius: Double,
    @ColumnInfo(name = "humidity_percent") val humidityPercent: Int,
    @ColumnInfo(name = "pressure_hpa") val pressureHpa: Int,
    @ColumnInfo(name = "wind_speed_mps") val windSpeedMetresPerSecond: Double,
    @ColumnInfo(name = "wind_direction_degrees") val windDirectionDegrees: Int,
    @ColumnInfo(name = "cloudiness_percent") val cloudinessPercent: Int,
    @ColumnInfo(name = "visibility_metres") val visibilityMetres: Int,
    @ColumnInfo(name = "sunrise_epoch") val sunriseEpochSeconds: Long,
    @ColumnInfo(name = "sunset_epoch") val sunsetEpochSeconds: Long,
    @ColumnInfo(name = "observed_at_epoch") val observedAtEpochSeconds: Long,
    @ColumnInfo(name = "cached_at_epoch") val cachedAtEpochSeconds: Long,
)

/**
 * Cached forecast, stored as one row per 3-hourly reading rather than a serialised
 * blob. Rows are queryable and indexable, so "the next 24 hours" is a `WHERE`
 * clause instead of a full deserialise-and-filter.
 */
@Entity(
    tableName = "cached_forecast_readings",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["location_id", "time_epoch"], unique = true)],
)
data class CachedForecastReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "time_epoch") val timeEpochSeconds: Long,
    @ColumnInfo(name = "condition_id") val conditionId: Int,
    val description: String,
    @ColumnInfo(name = "icon_code") val iconCode: String,
    @ColumnInfo(name = "temperature_celsius") val temperatureCelsius: Double,
    @ColumnInfo(name = "precipitation_probability") val precipitationProbability: Double,
    @ColumnInfo(name = "wind_speed_mps") val windSpeedMetresPerSecond: Double,
    @ColumnInfo(name = "cached_at_epoch") val cachedAtEpochSeconds: Long,
)
