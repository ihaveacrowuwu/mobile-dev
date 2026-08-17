package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.local.entity.CachedForecastReadingEntity
import com.nauhaan.skycast.data.local.entity.CachedWeatherEntity
import com.nauhaan.skycast.data.local.entity.SavedLocationEntity
import com.nauhaan.skycast.data.remote.dto.CurrentWeatherDto
import com.nauhaan.skycast.data.remote.dto.ForecastResponseDto
import com.nauhaan.skycast.data.remote.dto.GeocodingResultDto
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.ForecastDay
import com.nauhaan.skycast.domain.model.HourlyForecast
import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.model.WeatherCondition
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Every conversion between wire/storage shapes and domain models.
 *
 * These are **pure functions with no dependencies**, which makes them the cheapest
 * and highest-value unit tests in the project; see `WeatherMapperTest`.
 *
 * Keeping all mapping here means an OpenWeather response change touches this file
 * and nothing else.
 */
object WeatherMapper {
    // ── Remote → Domain ────────────────────────────────────────────────────

    fun CurrentWeatherDto.toDomain(locationId: Long, locationName: String, cachedAt: Instant): Weather {
        // OpenWeather always sends at least one entry, but defaulting keeps a
        // malformed response from crashing the app.
        val primary = weather.firstOrNull()
        return Weather(
            locationId = locationId,
            locationName = locationName.ifBlank { cityName },
            condition = WeatherCondition.fromOpenWeatherId(primary?.id ?: 0),
            description = primary?.description?.replaceFirstChar(Char::uppercase).orEmpty(),
            iconCode = primary?.icon.orEmpty(),
            temperatureCelsius = main.temperature,
            feelsLikeCelsius = main.feelsLike,
            minTemperatureCelsius = main.temperatureMin,
            maxTemperatureCelsius = main.temperatureMax,
            humidityPercent = main.humidity,
            pressureHpa = main.pressure,
            windSpeedMetresPerSecond = wind.speed,
            windDirectionDegrees = wind.degrees,
            cloudinessPercent = clouds.cloudinessPercent,
            visibilityMetres = visibility,
            sunrise = Instant.ofEpochSecond(system.sunriseEpochSeconds),
            sunset = Instant.ofEpochSecond(system.sunsetEpochSeconds),
            observedAt = Instant.ofEpochSecond(observedAtEpochSeconds),
            cachedAt = cachedAt,
            zoneOffset = safeOffset(timezoneOffsetSeconds),
        )
    }

    /**
     * Groups the flat 3-hourly list into calendar days.
     *
     * Days are keyed in the **location's** timezone, not the device's: a forecast for
     * Male' viewed from London must still be grouped by Maldivian days.
     */
    fun ForecastResponseDto.toDomain(locationId: Long, locationName: String, cachedAt: Instant): Forecast {
        val offset = safeOffset(city.timezoneOffsetSeconds)
        val zone: ZoneId = offset

        val days =
            readings
                .groupBy { Instant.ofEpochSecond(it.timeEpochSeconds).atZone(zone).toLocalDate() }
                .toSortedMap()
                .map { (date, dayReadings) ->
                    // The reading nearest midday best represents the day as a whole; the
                    // 03:00 reading would make every day look clear and cold.
                    val representative =
                        dayReadings.minByOrNull { reading ->
                            val hour = Instant.ofEpochSecond(reading.timeEpochSeconds).atZone(zone).hour
                            kotlin.math.abs(hour - MIDDAY_HOUR)
                        } ?: dayReadings.first()
                    val primary = representative.weather.firstOrNull()

                    ForecastDay(
                        date = date,
                        condition = WeatherCondition.fromOpenWeatherId(primary?.id ?: 0),
                        description = primary?.description?.replaceFirstChar(Char::uppercase).orEmpty(),
                        iconCode = primary?.icon.orEmpty(),
                        minTemperatureCelsius = dayReadings.minOf { it.main.temperatureMin },
                        maxTemperatureCelsius = dayReadings.maxOf { it.main.temperatureMax },
                        precipitationProbability = dayReadings.maxOf { it.precipitationProbability },
                        hourly = dayReadings.map { reading ->
                            HourlyForecast(
                                time = Instant.ofEpochSecond(reading.timeEpochSeconds),
                                condition = WeatherCondition.fromOpenWeatherId(
                                    reading.weather.firstOrNull()?.id ?: 0,
                                ),
                                iconCode = reading.weather
                                    .firstOrNull()
                                    ?.icon
                                    .orEmpty(),
                                temperatureCelsius = reading.main.temperature,
                                precipitationProbability = reading.precipitationProbability,
                                windSpeedMetresPerSecond = reading.wind.speed,
                            )
                        },
                    )
                }

        return Forecast(
            locationId = locationId,
            locationName = locationName.ifBlank { city.name },
            days = days,
            cachedAt = cachedAt,
            zoneOffset = offset,
        )
    }

    fun GeocodingResultDto.toDomain(): LocationSearchResult = LocationSearchResult(
        name = name,
        countryCode = country,
        state = state,
        latitude = latitude,
        longitude = longitude,
    )

    // ── Domain → Local ─────────────────────────────────────────────────────

    fun Weather.toEntity(): CachedWeatherEntity = CachedWeatherEntity(
        locationId = locationId,
        locationName = locationName,
        conditionId = condition.ordinal,
        description = description,
        iconCode = iconCode,
        temperatureCelsius = temperatureCelsius,
        feelsLikeCelsius = feelsLikeCelsius,
        minTemperatureCelsius = minTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        humidityPercent = humidityPercent,
        pressureHpa = pressureHpa,
        windSpeedMetresPerSecond = windSpeedMetresPerSecond,
        windDirectionDegrees = windDirectionDegrees,
        cloudinessPercent = cloudinessPercent,
        visibilityMetres = visibilityMetres,
        sunriseEpochSeconds = sunrise.epochSecond,
        sunsetEpochSeconds = sunset.epochSecond,
        observedAtEpochSeconds = observedAt.epochSecond,
        cachedAtEpochSeconds = cachedAt.epochSecond,
        timezoneOffsetSeconds = zoneOffset.totalSeconds,
    )

    fun Forecast.toEntities(): List<CachedForecastReadingEntity> = days.flatMap { day ->
        day.hourly.map { hour ->
            CachedForecastReadingEntity(
                locationId = locationId,
                locationName = locationName,
                timeEpochSeconds = hour.time.epochSecond,
                conditionId = hour.condition.ordinal,
                description = day.description,
                iconCode = hour.iconCode,
                temperatureCelsius = hour.temperatureCelsius,
                precipitationProbability = hour.precipitationProbability,
                windSpeedMetresPerSecond = hour.windSpeedMetresPerSecond,
                cachedAtEpochSeconds = cachedAt.epochSecond,
                timezoneOffsetSeconds = zoneOffset.totalSeconds,
            )
        }
    }

    // ── Local → Domain ─────────────────────────────────────────────────────

    fun CachedWeatherEntity.toDomain(): Weather = Weather(
        locationId = locationId,
        locationName = locationName,
        condition = WeatherCondition.entries.getOrElse(conditionId) { WeatherCondition.UNKNOWN },
        description = description,
        iconCode = iconCode,
        temperatureCelsius = temperatureCelsius,
        feelsLikeCelsius = feelsLikeCelsius,
        minTemperatureCelsius = minTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        humidityPercent = humidityPercent,
        pressureHpa = pressureHpa,
        windSpeedMetresPerSecond = windSpeedMetresPerSecond,
        windDirectionDegrees = windDirectionDegrees,
        cloudinessPercent = cloudinessPercent,
        visibilityMetres = visibilityMetres,
        sunrise = Instant.ofEpochSecond(sunriseEpochSeconds),
        sunset = Instant.ofEpochSecond(sunsetEpochSeconds),
        observedAt = Instant.ofEpochSecond(observedAtEpochSeconds),
        cachedAt = Instant.ofEpochSecond(cachedAtEpochSeconds),
        zoneOffset = safeOffset(timezoneOffsetSeconds),
    )

    /**
     * Rebuilds the day grouping from cached rows.
     *
     * Groups in the **location's** timezone, exactly as the remote path above does. Using the
     * device's zone here instead would make a day's boundary depend on
     * which path served the read, so the `epochDay` captured from a freshly-fetched list could
     * fail to resolve against the same forecast reloaded from cache.
     */
    fun List<CachedForecastReadingEntity>.toDomainForecast(): Forecast? {
        val first = firstOrNull() ?: return null
        val offset = safeOffset(first.timezoneOffsetSeconds)
        val zone: ZoneId = offset

        val days =
            groupBy { Instant.ofEpochSecond(it.timeEpochSeconds).atZone(zone).toLocalDate() }
                .toSortedMap()
                .map { (date, readings) ->
                    val representative =
                        readings.minByOrNull { reading ->
                            val hour = Instant.ofEpochSecond(reading.timeEpochSeconds).atZone(zone).hour
                            kotlin.math.abs(hour - MIDDAY_HOUR)
                        } ?: readings.first()

                    ForecastDay(
                        date = date,
                        condition = WeatherCondition.entries
                            .getOrElse(representative.conditionId) { WeatherCondition.UNKNOWN },
                        description = representative.description,
                        iconCode = representative.iconCode,
                        minTemperatureCelsius = readings.minOf { it.temperatureCelsius },
                        maxTemperatureCelsius = readings.maxOf { it.temperatureCelsius },
                        precipitationProbability = readings.maxOf { it.precipitationProbability },
                        hourly = readings.map { reading ->
                            HourlyForecast(
                                time = Instant.ofEpochSecond(reading.timeEpochSeconds),
                                condition = WeatherCondition.entries
                                    .getOrElse(reading.conditionId) { WeatherCondition.UNKNOWN },
                                iconCode = reading.iconCode,
                                temperatureCelsius = reading.temperatureCelsius,
                                precipitationProbability = reading.precipitationProbability,
                                windSpeedMetresPerSecond = reading.windSpeedMetresPerSecond,
                            )
                        },
                    )
                }

        return Forecast(
            locationId = first.locationId,
            locationName = first.locationName,
            days = days,
            cachedAt = Instant.ofEpochSecond(first.cachedAtEpochSeconds),
            zoneOffset = offset,
        )
    }

    // ── SavedLocation ↔ Entity ─────────────────────────────────────────────

    fun SavedLocationEntity.toDomain(): SavedLocation = SavedLocation(
        id = id,
        name = name,
        countryCode = countryCode,
        state = state,
        latitude = latitude,
        longitude = longitude,
        sortOrder = sortOrder,
        isPrimary = isPrimary,
    )

    fun SavedLocation.toEntity(): SavedLocationEntity = SavedLocationEntity(
        id = id,
        name = name,
        countryCode = countryCode,
        state = state,
        latitude = latitude,
        longitude = longitude,
        sortOrder = sortOrder,
        isPrimary = isPrimary,
    )

    fun LocationSearchResult.toEntity(sortOrder: Int, isPrimary: Boolean): SavedLocationEntity = SavedLocationEntity(
        name = name,
        countryCode = countryCode,
        state = state,
        latitude = latitude,
        longitude = longitude,
        sortOrder = sortOrder,
        isPrimary = isPrimary,
    )

    /**
     * Builds a [ZoneOffset] from a raw seconds value, falling back to UTC.
     *
     * `ZoneOffset.ofTotalSeconds` throws outside ±18 hours. A malformed offset from the API, or
     * a corrupted cache row, must not crash the mapper, and UTC is the only defensible default.
     */
    private fun safeOffset(totalSeconds: Int): ZoneOffset =
        runCatching { ZoneOffset.ofTotalSeconds(totalSeconds) }.getOrDefault(ZoneOffset.UTC)

    /** Local noon: the hour whose reading best characterises a whole day. */
    private const val MIDDAY_HOUR = 12
}
