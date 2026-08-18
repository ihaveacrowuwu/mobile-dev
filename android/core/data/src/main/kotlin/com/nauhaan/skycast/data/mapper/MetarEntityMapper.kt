package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.local.entity.CachedMetarEntity
import com.nauhaan.skycast.domain.model.CloudLayer
import com.nauhaan.skycast.domain.model.FlightCategory
import com.nauhaan.skycast.domain.model.MetarReport
import java.time.Instant

/**
 * Between the METAR cache row and the domain report.
 *
 * The cloud layers round-trip through one string rather than a child table, since they are only
 * ever read and written whole: `COVER:BASE` pairs separated by `;`, with an empty base for a clear
 * sky. Parsing is forgiving, so a row written by an older build cannot crash a newer one.
 */
internal fun CachedMetarEntity.toDomain(): MetarReport = MetarReport(
    stationId = stationId,
    stationName = stationName,
    distanceKm = distanceKm,
    latitude = latitude,
    longitude = longitude,
    elevationMetres = elevationMetres,
    observedAt = Instant.ofEpochSecond(observedAtEpochSeconds),
    temperatureCelsius = temperatureCelsius,
    dewPointCelsius = dewPointCelsius,
    windDirectionDegrees = windDirectionDegrees,
    windSpeedKnots = windSpeedKnots,
    visibilityStatuteMiles = visibilityStatuteMiles,
    visibilityIsOrGreater = visibilityIsOrGreater,
    altimeterHectopascals = altimeterHectopascals,
    clouds = clouds.toCloudLayers(),
    flightCategory = FlightCategory.from(flightCategory),
    raw = raw,
    cachedAt = Instant.ofEpochSecond(cachedAtEpochSeconds),
)

internal fun MetarReport.toEntity(locationId: Long): CachedMetarEntity = CachedMetarEntity(
    locationId = locationId,
    stationId = stationId,
    stationName = stationName,
    distanceKm = distanceKm,
    latitude = latitude,
    longitude = longitude,
    elevationMetres = elevationMetres,
    observedAtEpochSeconds = observedAt.epochSecond,
    temperatureCelsius = temperatureCelsius,
    dewPointCelsius = dewPointCelsius,
    windDirectionDegrees = windDirectionDegrees,
    windSpeedKnots = windSpeedKnots,
    visibilityStatuteMiles = visibilityStatuteMiles,
    visibilityIsOrGreater = visibilityIsOrGreater,
    altimeterHectopascals = altimeterHectopascals,
    clouds = clouds.toStoredString(),
    flightCategory = flightCategory.name,
    raw = raw,
    cachedAtEpochSeconds = cachedAt.epochSecond,
)

internal fun List<CloudLayer>.toStoredString(): String =
    joinToString(LAYER_SEPARATOR) { "${it.cover}$FIELD_SEPARATOR${it.baseFeet ?: ""}" }

internal fun String.toCloudLayers(): List<CloudLayer> = split(LAYER_SEPARATOR)
    .filter { it.isNotBlank() }
    .mapNotNull { entry ->
        val parts = entry.split(FIELD_SEPARATOR)
        val cover = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CloudLayer(cover = cover, baseFeet = parts.getOrNull(1)?.toIntOrNull())
    }

private const val LAYER_SEPARATOR = ";"
private const val FIELD_SEPARATOR = ":"
