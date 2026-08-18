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
 * `onDelete = CASCADE` means removing a location automatically drops its cache, so there are no
 * orphan rows and no manual cleanup code.
 *
 * `location_id` is the primary key, and is the natural one: there is exactly one row per location.
 * Room implements upsert as "INSERT, and on a uniqueness conflict UPDATE … **WHERE id = ?**", so
 * with the natural key as the primary key the conflict and the update target the same column.
 * `SkyCastDatabaseTest.upsertWeatherReplacesTheRow` guards it.
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
)
data class CachedWeatherEntity(
    @PrimaryKey @ColumnInfo(name = "location_id") val locationId: Long,
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
    /**
     * The location's UTC offset in seconds. Defaults to 0 so the v1→v2 migration needs no
     * backfill: a cached row written before this column existed reads as UTC, and is
     * replaced by a correct one at the next refresh (within the 10-minute TTL).
     */
    @ColumnInfo(name = "timezone_offset_seconds", defaultValue = "0")
    val timezoneOffsetSeconds: Int = 0,
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
    // Composite natural key, for the same reason CachedWeatherEntity uses one: a surrogate id
    // beside a unique index makes @Upsert a silent no-op on the second write.
    primaryKeys = ["location_id", "time_epoch"],
)
data class CachedForecastReadingEntity(
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
    /** See [CachedWeatherEntity.timezoneOffsetSeconds]. */
    @ColumnInfo(name = "timezone_offset_seconds", defaultValue = "0")
    val timezoneOffsetSeconds: Int = 0,
)

/**
 * The nearest airport's METAR for one saved location.
 *
 * Keyed by `locationId`, like the weather cache and for the same reason: one row per place, replaced
 * on refresh rather than accumulating. A foreign key with `CASCADE` means removing a place takes its
 * observation with it.
 *
 * The cloud layers are stored as one string rather than a child table. They are read and written only
 * as a whole, never queried, and a table plus a join for "FEW045 SCT120" would be structure with no
 * purpose. Format is `COVER:BASE` pairs separated by `;`, with an empty base for a clear sky.
 */
@Entity(
    tableName = "cached_metar",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CachedMetarEntity(
    @PrimaryKey
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "station_id") val stationId: String,
    @ColumnInfo(name = "station_name") val stationName: String,
    @ColumnInfo(name = "distance_km") val distanceKm: Double,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "elevation_metres") val elevationMetres: Int,
    @ColumnInfo(name = "observed_at") val observedAtEpochSeconds: Long,
    @ColumnInfo(name = "temperature_celsius") val temperatureCelsius: Double?,
    @ColumnInfo(name = "dew_point_celsius") val dewPointCelsius: Double?,
    @ColumnInfo(name = "wind_direction_degrees") val windDirectionDegrees: Int?,
    @ColumnInfo(name = "wind_speed_knots") val windSpeedKnots: Int?,
    @ColumnInfo(name = "visibility_statute_miles") val visibilityStatuteMiles: Double?,
    @ColumnInfo(name = "visibility_is_or_greater") val visibilityIsOrGreater: Boolean,
    @ColumnInfo(name = "altimeter_hectopascals") val altimeterHectopascals: Double?,
    @ColumnInfo(name = "clouds") val clouds: String,
    @ColumnInfo(name = "flight_category") val flightCategory: String,
    @ColumnInfo(name = "raw") val raw: String,
    @ColumnInfo(name = "cached_at") val cachedAtEpochSeconds: Long,
)
