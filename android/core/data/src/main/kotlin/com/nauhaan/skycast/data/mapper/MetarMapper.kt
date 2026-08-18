package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.remote.dto.MetarDto
import com.nauhaan.skycast.domain.model.CloudLayer
import com.nauhaan.skycast.domain.model.FlightCategory
import com.nauhaan.skycast.domain.model.MetarReport
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the aviation API's station list into one domain report.
 *
 * Two jobs: choosing which station, and decoding the fields the API leaves loosely typed.
 */
object MetarMapper {
    /**
     * The report from the station nearest [latitude], [longitude], or `null` if none is usable.
     *
     * The API has no "nearest station" query, so the caller asks for a bounding box and the choice
     * happens here. Nearest by great-circle distance rather than by which the API happened to list
     * first: the response comes back in no particular order, and for London the first entry is Biggin
     * Hill while Heathrow is both closer to the city and the one anyone would expect.
     *
     * Stations without coordinates or without a raw report are skipped rather than mapped into a
     * half-empty card, an entry with no observation in it is not a reading.
     */
    fun nearestReport(stations: List<MetarDto>, latitude: Double, longitude: Double, cachedAt: Instant): MetarReport? =
        stations
            .mapNotNull { dto ->
                val stationLat = dto.latitude ?: return@mapNotNull null
                val stationLon = dto.longitude ?: return@mapNotNull null
                if (dto.raw.isNullOrBlank() || dto.icaoId.isNullOrBlank()) return@mapNotNull null
                dto to distanceKm(latitude, longitude, stationLat, stationLon)
            }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (dto, distance) -> report(dto, distance, cachedAt) }

    private fun report(dto: MetarDto, distanceKm: Double, cachedAt: Instant): MetarReport = MetarReport(
        stationId = dto.icaoId.orEmpty(),
        stationName = dto.name.orEmpty(),
        distanceKm = distanceKm,
        latitude = dto.latitude ?: 0.0,
        longitude = dto.longitude ?: 0.0,
        elevationMetres = dto.elevationMetres ?: 0,
        observedAt = dto.observedAtEpochSeconds
            ?.let(Instant::ofEpochSecond)
            ?: cachedAt,
        temperatureCelsius = dto.temperatureCelsius,
        dewPointCelsius = dto.dewPointCelsius,
        windDirectionDegrees = dto.windDirectionDegrees,
        windSpeedKnots = dto.windSpeedKnots,
        visibilityStatuteMiles = dto.visibilityStatuteMiles,
        visibilityIsOrGreater = dto.visibilityIsOrGreater,
        altimeterHectopascals = dto.altimeterHectopascals,
        clouds = dto.clouds.mapNotNull { layer ->
            layer.cover?.let { CloudLayer(cover = it, baseFeet = layer.baseFeet) }
        },
        flightCategory = FlightCategory.from(dto.flightCategory),
        raw = dto.raw.orEmpty(),
        cachedAt = cachedAt,
    )

    /**
     * Great-circle distance in kilometres, by the haversine formula.
     *
     * Exact enough by a wide margin: the alternative is straight-line distance on latitude and
     * longitude treated as a plane, which at these scales would be fine near the equator and wrong
     * by a useful fraction near the poles, and one of the two seeded places is at 4°N and the other
     * at 51°N, so the difference is not hypothetical.
     */
    internal fun distanceKm(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val deltaLat = Math.toRadians(toLat - fromLat)
        val deltaLon = Math.toRadians(toLon - fromLon)
        val a = sin(deltaLat / 2).pow(2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * sin(deltaLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Mean radius; the difference from the polar or equatorial figure is far below what matters. */
    private const val EARTH_RADIUS_KM = 6371.0
}
