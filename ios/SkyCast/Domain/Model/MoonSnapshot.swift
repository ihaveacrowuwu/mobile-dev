import Foundation

/// The eight phase names people actually use.
///
/// The four *principal* phases (new, first quarter, full, last quarter) are instants. The four
/// *intermediate* ones are the stretches between them, which is why ``MoonSnapshot/phase`` is
/// derived from a range but ``PrincipalPhase`` carries a precise `Date`.
enum MoonPhaseName: String, CaseIterable, Sendable, Codable {
    case new
    case waxingCrescent
    case firstQuarter
    case waxingGibbous
    case full
    case waningGibbous
    case lastQuarter
    case waningCrescent

    var label: String {
        switch self {
        case .new: "New moon"
        case .waxingCrescent: "Waxing crescent"
        case .firstQuarter: "First quarter"
        case .waxingGibbous: "Waxing gibbous"
        case .full: "Full moon"
        case .waningGibbous: "Waning gibbous"
        case .lastQuarter: "Last quarter"
        case .waningCrescent: "Waning crescent"
        }
    }

    /// Only the four principal phases have a computable instant; the rest are spans.
    var isPrincipal: Bool {
        switch self {
        case .new, .firstQuarter, .full, .lastQuarter: true
        default: false
        }
    }

    /// The elongation at which this principal phase occurs, in degrees.
    var principalElongation: Double? {
        switch self {
        case .new: 0
        case .firstQuarter: 90
        case .full: 180
        case .lastQuarter: 270
        default: nil
        }
    }
}

/// How close the Moon is, as a band rather than a number.
enum MoonDistanceBand: Sendable {
    case veryClose
    case closer
    case average
    case further
    case veryFar
}

/// One dated principal phase, for the "what's next" list.
struct PrincipalPhase: Equatable, Identifiable, Sendable {
    let name: MoonPhaseName
    let date: Date

    var id: Date {
        date
    }
}

/// Everything the Moon tab knows, for one instant at one place.
///
/// Computed on the device, never fetched. Only `moonrise` and `moonset` depend on where you are;
/// the rest are the same for everyone on Earth.
struct MoonSnapshot: Equatable, Sendable {
    /// The instant this was computed for.
    let date: Date
    /// 0 at new, 1 at full.
    let illuminatedFraction: Double
    /// The Moon's elongation from the Sun, 0 to 360°. 0 is new, 180 is full.
    ///
    /// Drives the phase name, the illuminated fraction and the drawn terminator, so all three
    /// derive from one value.
    let elongationDegrees: Double
    /// Days since the last new moon.
    let ageDays: Double
    let phase: MoonPhaseName
    /// Centre-to-centre, in kilometres.
    let distanceKm: Double
    /// How wide the Moon looks, in degrees. Varies by about 12% over a month.
    let angularDiameterDegrees: Double
    /// `nil` when the Moon does not cross the horizon on this date at this latitude, which is not
    /// an error.
    let moonrise: Date?
    let moonset: Date?
    /// The next four principal phases, soonest first.
    let upcomingPhases: [PrincipalPhase]

    /// Growing towards full rather than shrinking towards new.
    var isWaxing: Bool {
        elongationDegrees < 180
    }

    /// Position through the lunar month, 0 at new and 1 at the next new.
    var cycleFraction: Double {
        elongationDegrees / 360
    }

    var illuminatedPercent: Int {
        Int((illuminatedFraction * 100).rounded())
    }

    /// Where tonight's distance sits between the closest and furthest the Moon gets, 0 to 1.
    ///
    /// The extremes are the *extreme* perigee and apogee rather than the mean ones, so the gauge
    /// never pins at either end.
    var distanceFraction: Double {
        let span = Self.apogeeKm - Self.perigeeKm
        return min(max((distanceKm - Self.perigeeKm) / span, 0), 1)
    }

    /// Which part of its range tonight's distance falls in.
    ///
    /// The band, not the wording: the thresholds are shared with Android and unit-tested on both
    /// platforms, while the sentence each one turns into belongs to the UI, where it can be
    /// localised. The extremes are the outer tenth at each end.
    var distanceBand: MoonDistanceBand {
        switch distanceFraction {
        case ..<0.10: .veryClose
        case ..<0.45: .closer
        case ..<0.55: .average
        case ..<0.90: .further
        default: .veryFar
        }
    }

    /// The next full moon, for the countdown.
    var nextFullMoon: PrincipalPhase? {
        upcomingPhases.first { $0.name == .full }
    }

    /// How long the Moon is above the horizon, when both ends are known.
    var timeAboveHorizon: TimeInterval? {
        guard let moonrise, let moonset else { return nil }
        // A set *before* the rise belongs to the previous night's moon, so the span runs to
        // tomorrow's set instead, which keeps the figure positive.
        let interval = moonset.timeIntervalSince(moonrise)
        return interval > 0 ? interval : interval + Self.day
    }

    /// Closest the Moon comes, in kilometres.
    static let perigeeKm: Double = 356_500
    /// Furthest the Moon gets, in kilometres.
    static let apogeeKm: Double = 406_700
    private static let day: TimeInterval = 86_400
}
