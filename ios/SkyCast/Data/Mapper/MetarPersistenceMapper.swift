import Foundation

/// Between the cached SwiftData model and the domain report.
///
/// The cloud layers round-trip through one string rather than a related model, since they are only
/// ever read and written whole: `COVER:BASE` pairs separated by `;`. Parsing is forgiving, so a row
/// written by an older build cannot crash a newer one.
///
/// Mirrors `MetarEntityMapper.kt` on Android, including the storage format.
enum MetarPersistenceMapper {
    static func report(from model: PersistentMetar) -> MetarReport {
        MetarReport(
            stationID: model.stationID,
            stationName: model.stationName,
            distanceKm: model.distanceKm,
            latitude: model.latitude,
            longitude: model.longitude,
            elevationMetres: model.elevationMetres,
            observedAt: model.observedAt,
            temperatureCelsius: model.temperatureCelsius,
            dewPointCelsius: model.dewPointCelsius,
            windDirectionDegrees: model.windDirectionDegrees,
            windSpeedKnots: model.windSpeedKnots,
            visibilityStatuteMiles: model.visibilityStatuteMiles,
            visibilityIsOrGreater: model.visibilityIsOrGreater,
            altimeterHectopascals: model.altimeterHectopascals,
            clouds: cloudLayers(from: model.clouds),
            flightCategory: FlightCategory.from(model.flightCategoryRawValue),
            raw: model.raw,
            cachedAt: model.cachedAt
        )
    }

    static func persistent(from report: MetarReport, locationID: Int64) -> PersistentMetar {
        PersistentMetar(
            locationID: locationID,
            stationID: report.stationID,
            stationName: report.stationName,
            distanceKm: report.distanceKm,
            latitude: report.latitude,
            longitude: report.longitude,
            elevationMetres: report.elevationMetres,
            observedAt: report.observedAt,
            temperatureCelsius: report.temperatureCelsius,
            dewPointCelsius: report.dewPointCelsius,
            windDirectionDegrees: report.windDirectionDegrees,
            windSpeedKnots: report.windSpeedKnots,
            visibilityStatuteMiles: report.visibilityStatuteMiles,
            visibilityIsOrGreater: report.visibilityIsOrGreater,
            altimeterHectopascals: report.altimeterHectopascals,
            clouds: storedString(from: report.clouds),
            flightCategoryRawValue: report.flightCategory.rawValue,
            raw: report.raw,
            cachedAt: report.cachedAt
        )
    }

    static func storedString(from layers: [CloudLayer]) -> String {
        layers
            .map { "\($0.cover)\(fieldSeparator)\($0.baseFeet.map(String.init) ?? "")" }
            .joined(separator: layerSeparator)
    }

    static func cloudLayers(from stored: String) -> [CloudLayer] {
        stored
            .split(separator: Character(layerSeparator))
            .compactMap { entry in
                let parts = entry.split(separator: Character(fieldSeparator), omittingEmptySubsequences: false)
                guard let cover = parts.first, !cover.isEmpty else { return nil }
                let base = parts.count > 1 ? Int(parts[1]) : nil
                return CloudLayer(cover: String(cover), baseFeet: base)
            }
    }

    private static let layerSeparator = ";"
    private static let fieldSeparator = ":"
}
