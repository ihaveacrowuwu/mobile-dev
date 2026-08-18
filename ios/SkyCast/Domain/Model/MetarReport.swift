import Foundation

/// A decoded aviation routine weather report from the nearest reporting airport.
///
/// METAR is the observation format pilots read before flying: issued by an airport, usually hourly,
/// in a fixed coded line, measured at a runway and quoted in aviation units.
///
/// The raw line is kept alongside the decoded fields as the provenance for them.
struct MetarReport: Equatable, Sendable, Identifiable {
    /// The four-letter ICAO code, e.g. `EGLL`.
    let stationID: String
    /// The airport's own name, e.g. "London/Heathrow Intl, EN, GB".
    let stationName: String
    /// How far the station is from the saved location, in kilometres.
    let distanceKm: Double
    let latitude: Double
    let longitude: Double
    /// Field elevation in metres.
    let elevationMetres: Int
    let observedAt: Date
    let temperatureCelsius: Double?
    let dewPointCelsius: Double?
    /// Degrees the wind blows *from*, or `nil` when the report says variable.
    let windDirectionDegrees: Int?
    let windSpeedKnots: Int?
    /// Statute miles. `nil` when the report omits it.
    let visibilityStatuteMiles: Double?
    /// Whether the visibility figure is a floor rather than a measurement, i.e. "10+".
    let visibilityIsOrGreater: Bool
    /// Altimeter setting in hectopascals: the "Q1010" group.
    let altimeterHectopascals: Double?
    let clouds: [CloudLayer]
    let flightCategory: FlightCategory
    /// The report exactly as issued.
    let raw: String
    /// When this record was written to the local cache. Drives staleness.
    let cachedAt: Date

    var id: String {
        stationID
    }

    /// Whether the cached copy is older than `ttl`.
    ///
    /// Thirty minutes, because a METAR is issued on the hour (and on the half hour as a SPECI when
    /// conditions change), so polling faster cannot produce a newer observation.
    func isStale(now: Date, ttl: TimeInterval = MetarReport.metarTTL) -> Bool {
        cachedAt.addingTimeInterval(ttl) < now
    }

    /// How old the observation itself is, which is what a pilot actually cares about.
    func age(now: Date) -> TimeInterval {
        max(0, now.timeIntervalSince(observedAt))
    }

    static let metarTTL: TimeInterval = 30 * 60
}

/// One reported cloud layer.
struct CloudLayer: Equatable, Sendable {
    /// The coverage abbreviation as issued: FEW, SCT, BKN, OVC, CLR, SKC.
    let cover: String
    /// Height of the layer's base above the field, in feet. `nil` for clear skies.
    let baseFeet: Int?
}

/// The flight-rules category the observation falls into.
///
/// It decides whether a flight can be made under visual rules. The API computes the category
/// itself, and its string is mapped here rather than re-derived, so the badge cannot disagree with
/// the source.
enum FlightCategory: String, Sendable, CaseIterable {
    /// Visual: ceiling above 3000 ft and visibility above 5 miles.
    case vfr = "VFR"
    /// Marginal visual: ceiling 1000–3000 ft or visibility 3–5 miles.
    case mvfr = "MVFR"
    /// Instrument: ceiling 500–1000 ft or visibility 1–3 miles.
    case ifr = "IFR"
    /// Low instrument: below 500 ft or below 1 mile.
    case lifr = "LIFR"
    /// The report did not include one.
    case unknown = ""

    static func from(_ code: String?) -> FlightCategory {
        guard let code else { return .unknown }
        return allCases.first { $0.rawValue.caseInsensitiveCompare(code) == .orderedSame } ?? .unknown
    }

    var label: String {
        rawValue
    }
}
