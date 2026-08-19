import Foundation

/// The state of the Earth's magnetic field, as NOAA reports it.
///
/// One number carries almost all of it: **Kp**, a 0–9 index of geomagnetic disturbance. It decides how far from
/// the poles the auroral oval reaches, which is what decides whether there is any point going outside to look.
/// See ``AuroraCalculator`` for the part that turns Kp into an answer for a particular place.
struct SpaceWeather: Equatable, Sendable {
    /// The most recent measured or estimated Kp.
    let kpNow: Double
    let observedAt: Date
    /// NOAA's storm scale, G1 to G5, when the disturbance is large enough to have one.
    let stormLevel: String?
    /// The three-hourly forecast ahead of ``observedAt``, soonest first.
    let upcoming: [KpPeriod]
    let cachedAt: Date

    /// Whether the cached copy is older than `ttl`.
    ///
    /// Kp is issued every three hours and re-estimated far more often, so half an hour keeps the figure current
    /// without spending requests on a number that has not moved.
    func isStale(now: Date, ttl: TimeInterval = SpaceWeather.spaceWeatherTTL) -> Bool {
        cachedAt.addingTimeInterval(ttl) < now
    }

    /// The highest Kp forecast for the next `withinHours` hours, and when.
    ///
    /// The figure worth planning an evening around: aurora is a "go outside at the right hour" event, and the
    /// current Kp says nothing about whether the interesting part has happened yet.
    func peakAhead(withinHours: Double = SpaceWeather.forecastWindowHours) -> KpPeriod? {
        let limit = observedAt.addingTimeInterval(withinHours * 3_600)
        return upcoming.filter { $0.time <= limit }.max { $0.kp < $1.kp }
    }

    static let spaceWeatherTTL: TimeInterval = 30 * 60

    /// Tonight, roughly: far enough ahead to cover an evening, not so far that the forecast is guesswork.
    static let forecastWindowHours: Double = 24
}

/// One three-hour Kp period.
struct KpPeriod: Equatable, Sendable {
    let time: Date
    let kp: Double
    /// NOAA's storm scale for the period, when it has one.
    let stormLevel: String?
}

/// How likely the aurora is to be visible.
///
/// Ordered, so a caller can ask whether the chance is at least something.
enum AuroraChance: Int, Comparable, Sendable {
    /// Not from here, at this Kp.
    case none
    /// A glow low on the poleward horizon, for a camera more than an eye.
    case faintOnHorizon
    /// Worth going outside and looking north.
    case possible
    /// The oval reaches this place: expect a display to the north.
    case likely
    /// Inside the oval, overhead, not on the horizon.
    case overhead

    static func < (lhs: AuroraChance, rhs: AuroraChance) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}
