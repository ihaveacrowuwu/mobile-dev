import Foundation

/// Turns the aviation API's station list into one domain report.
///
/// Two jobs: choosing which station, and interpreting the fields the API leaves loosely typed.
///
/// Mirrors `MetarMapper.kt` on Android, including the choice of nearest-by-distance.
enum MetarMapper {
    /// The report from the station nearest the coordinates, or `nil` if none is usable.
    ///
    /// The API has no "nearest station" query, so the caller asks for a bounding box and the choice
    /// happens here. Nearest by great-circle distance rather than by which the API happened to list
    /// first: the response comes back in no particular order, and for London the first entry is
    /// Biggin Hill while London City is nearer the centre.
    ///
    /// Stations without coordinates or without a raw report are skipped rather than mapped into a
    /// half-empty card, an entry with no observation in it is not a reading.
    static func nearestReport(
        from stations: [MetarDTO],
        latitude: Double,
        longitude: Double,
        cachedAt: Date
    )
        -> MetarReport?
    {
        let usable = stations.compactMap { dto -> (MetarDTO, Double)? in
            guard let stationLatitude = dto.latitude,
                  let stationLongitude = dto.longitude,
                  let raw = dto.raw, !raw.isEmpty,
                  let icao = dto.icaoID, !icao.isEmpty
            else { return nil }
            return (
                dto,
                distanceKm(
                    fromLatitude: latitude,
                    fromLongitude: longitude,
                    toLatitude: stationLatitude,
                    toLongitude: stationLongitude
                )
            )
        }

        guard let (dto, distance) = usable.min(by: { $0.1 < $1.1 }) else { return nil }
        return report(from: dto, distanceKm: distance, cachedAt: cachedAt)
    }

    private static func report(from dto: MetarDTO, distanceKm: Double, cachedAt: Date) -> MetarReport {
        MetarReport(
            stationID: dto.icaoID ?? "",
            stationName: dto.name ?? "",
            distanceKm: distanceKm,
            latitude: dto.latitude ?? 0,
            longitude: dto.longitude ?? 0,
            elevationMetres: dto.elevationMetres ?? 0,
            observedAt: dto.observedAtEpochSeconds.map { Date(timeIntervalSince1970: $0) } ?? cachedAt,
            temperatureCelsius: dto.temperatureCelsius,
            dewPointCelsius: dto.dewPointCelsius,
            // A variable wind has no bearing, and `nil` says so. Zero would be due north, which is a
            // direction the observation explicitly declined to give.
            windDirectionDegrees: dto.windDirection?.value.map { Int($0) },
            windSpeedKnots: dto.windSpeedKnots,
            visibilityStatuteMiles: dto.visibility?.value,
            visibilityIsOrGreater: dto.visibility?.isOrGreater ?? false,
            altimeterHectopascals: dto.altimeterHectopascals,
            clouds: (dto.clouds ?? []).compactMap { layer in
                layer.cover.map { CloudLayer(cover: $0, baseFeet: layer.baseFeet) }
            },
            flightCategory: FlightCategory.from(dto.flightCategory),
            raw: dto.raw ?? "",
            cachedAt: cachedAt
        )
    }

    /// Great-circle distance in kilometres, by the haversine formula.
    ///
    /// Exact enough by a wide margin. The alternative is treating latitude and longitude as a plane,
    /// which is fine near the equator and wrong by a useful fraction further north, and one of the
    /// two seeded places is at 4°N and the other at 51°N, so the difference is not hypothetical.
    static func distanceKm(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    )
        -> Double
    {
        let deltaLatitude = (toLatitude - fromLatitude) * .pi / 180
        let deltaLongitude = (toLongitude - fromLongitude) * .pi / 180
        let chord = pow(sin(deltaLatitude / 2), 2)
            + cos(fromLatitude * .pi / 180) * cos(toLatitude * .pi / 180) * pow(sin(deltaLongitude / 2), 2)
        return earthRadiusKm * 2 * atan2(sqrt(chord), sqrt(1 - chord))
    }

    /// Mean radius; the difference from the polar or equatorial figure is far below what matters.
    private static let earthRadiusKm = 6_371.0
}
