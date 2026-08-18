import Foundation

/// The Moon, computed from the date.
///
/// The series are the standard truncated forms from Meeus, *Astronomical Algorithms* (2nd ed.),
/// chapters 22, 25 and 47, keeping the largest periodic terms only.
///
/// Accuracy measured against PyEphem across every third day of 2026 and 24 principal phases.
/// Worst-case error: **0.36 percentage points** of illumination, **153 km** of distance,
/// **5.2 minutes** on a phase instant, and **under a minute** on moonrise for London and Malé.
///
/// The Kotlin twin is `MoonCalculator.kt`, checked against the same reference values.
enum MoonCalculator {
    // MARK: - Public API

    /// The full picture for one instant at one place.
    ///
    /// - Parameters:
    ///   - date: the instant to compute for.
    ///   - latitude: degrees north, for moonrise and moonset.
    ///   - longitude: degrees east, for moonrise and moonset.
    ///   - timeZone: the zone whose local day moonrise and moonset are searched within. Rise and set
    ///     belong to a *day*, and which day depends on the observer's clock, not on UTC.
    static func snapshot(
        for date: Date,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = .current
    )
        -> MoonSnapshot
    {
        let elongation = elongationDegrees(at: date)
        let position = moonPosition(centuries: centuries(since: date))
        let (rise, set) = riseAndSet(on: date, latitude: latitude, longitude: longitude, timeZone: timeZone)

        return MoonSnapshot(
            date: date,
            illuminatedFraction: illuminatedFraction(elongationDegrees: elongation),
            elongationDegrees: elongation,
            ageDays: synodicMonth * elongation / 360,
            phase: phaseName(forElongation: elongation),
            distanceKm: position.distanceKm,
            angularDiameterDegrees: angularDiameterDegrees(distanceKm: position.distanceKm),
            moonrise: rise,
            moonset: set,
            upcomingPhases: upcomingPhases(after: date)
        )
    }

    /// The illuminated fraction of the disc, 0 at new and 1 at full.
    ///
    /// The phase angle Sun–Moon–Earth is 180° − elongation, and the illuminated fraction is
    /// (1 + cos(phase angle)) / 2, which reduces to this.
    static func illuminatedFraction(elongationDegrees: Double) -> Double {
        (1 - cos(elongationDegrees * .pi / 180)) / 2
    }

    /// Which of the eight named phases an elongation falls in.
    ///
    /// The principal phases get a narrow band around their exact angle rather than an eighth of the
    /// circle each, so the label always matches the drawn disc.
    static func phaseName(forElongation elongation: Double) -> MoonPhaseName {
        let angle = normalisedDegrees(elongation)
        let band = principalBandDegrees
        switch angle {
        case ..<band, (360 - band)...: return .new
        case ..<(90 - band): return .waxingCrescent
        case ..<(90 + band): return .firstQuarter
        case ..<(180 - band): return .waxingGibbous
        case ..<(180 + band): return .full
        case ..<(270 - band): return .waningGibbous
        case ..<(270 + band): return .lastQuarter
        default: return .waningCrescent
        }
    }

    /// The Moon's elongation from the Sun in ecliptic longitude, 0–360°.
    static func elongationDegrees(at date: Date) -> Double {
        let centuries = centuries(since: date)
        return normalisedDegrees(moonPosition(centuries: centuries).longitude - sunLongitude(centuries: centuries))
    }

    /// The next four principal phases after `date`, soonest first.
    static func upcomingPhases(after date: Date) -> [PrincipalPhase] {
        MoonPhaseName.allCases
            .compactMap { name -> PrincipalPhase? in
                guard let target = name.principalElongation,
                      let instant = nextTime(elongation: target, after: date)
                else { return nil }
                return PrincipalPhase(name: name, date: instant)
            }
            .sorted { $0.date < $1.date }
    }

    /// The next instant after `date` at which the Moon's elongation equals `elongation`.
    ///
    /// Found by scanning for a sign change in the wrapped difference, then bisecting, which reuses
    /// the position model already here.
    static func nextTime(elongation target: Double, after date: Date, searchingDays: Double = 40) -> Date? {
        func difference(_ at: Date) -> Double {
            // Wrapped to -180...180 so the root is a clean sign change rather than a 360° cliff.
            normalisedDegrees(elongationDegrees(at: at) - target + 180) - 180
        }

        var lower = date
        var lowerValue = difference(lower)
        let limit = date.addingTimeInterval(searchingDays * 86_400)

        while lower < limit {
            let upper = lower.addingTimeInterval(scanStepSeconds)
            let upperValue = difference(upper)
            if lowerValue < 0, upperValue >= 0 {
                return bisect(from: lower, to: upper, difference: difference)
            }
            lower = upper
            lowerValue = upperValue
        }
        return nil
    }

    /// Moonrise and moonset within the local day containing `date`.
    ///
    /// Both are optional and independently so: at high latitudes the Moon can rise without setting
    /// inside one day, or neither. They are searched for separately rather than assumed to be a
    /// pair.
    static func riseAndSet(
        on date: Date,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone
    )
        -> (rise: Date?, set: Date?)
    {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let start = calendar.startOfDay(for: date)

        func altitudeAboveHorizon(_ at: Date) -> Double {
            altitudeDegrees(at: at, latitude: latitude, longitude: longitude) - horizonDegrees
        }

        var rise: Date?
        var set: Date?
        var previous = start
        var previousAltitude = altitudeAboveHorizon(previous)

        for step in 1...riseSetSteps {
            let current = start.addingTimeInterval(Double(step) * riseSetStepSeconds)
            let currentAltitude = altitudeAboveHorizon(current)

            if previousAltitude < 0, currentAltitude >= 0, rise == nil {
                rise = bisect(from: previous, to: current, difference: altitudeAboveHorizon)
            } else if previousAltitude >= 0, currentAltitude < 0, set == nil {
                set = bisect(from: previous, to: current, difference: altitudeAboveHorizon)
            }
            previous = current
            previousAltitude = currentAltitude
        }
        return (rise, set)
    }

    /// The Moon's altitude above the true horizon, in degrees.
    static func altitudeDegrees(at date: Date, latitude: Double, longitude: Double) -> Double {
        let julian = julianDay(date)
        let position = moonPosition(centuries: centuries(since: date))
        let (rightAscension, declination) = equatorial(
            longitude: position.longitude,
            latitude: position.latitude,
            centuries: centuries(since: date)
        )
        let hourAngle = radians(normalisedDegrees(greenwichSiderealDegrees(julianDay: julian) + longitude))
            - rightAscension
        let observerLatitude = radians(latitude)
        let sine = sin(observerLatitude) * sin(declination)
            + cos(observerLatitude) * cos(declination) * cos(hourAngle)
        return degrees(asin(min(max(sine, -1), 1)))
    }

    // MARK: - Position

    /// Ecliptic longitude and latitude in degrees, and distance in kilometres.
    struct MoonPosition: Equatable {
        let longitude: Double
        let latitude: Double
        let distanceKm: Double
    }

    /// Meeus ch. 47, truncated to the dominant periodic terms.
    ///
    /// The coefficients are the published ones, left as literals so they can be checked against the
    /// book.
    static func moonPosition(centuries time: Double) -> MoonPosition {
        // Mean elements. Meeus's symbol for each is in a trailing comment below, so the series can
        // still be checked line by line against the book now that the names are spelled out.
        let meanLongitude = 218.3164477 + 481_267.88123421 * time - 0.0015786 * time * time
        let meanElongation = 297.8501921 + 445_267.1114034 * time - 0.0018819 * time * time
        let sunAnomaly = 357.5291092 + 35_999.0502909 * time - 0.0001536 * time * time
        let moonAnomaly = 134.9633964 + 477_198.8675055 * time + 0.0087414 * time * time
        let argumentOfLatitude = 93.2720950 + 483_202.0175233 * time - 0.0036539 * time * time

        let elongation = radians(meanElongation) // D
        let sunAnomalyRadians = radians(sunAnomaly) // M
        let moonAnomalyRadians = radians(moonAnomaly) // M′
        let latitudeArgument = radians(argumentOfLatitude) // F

        let longitude = meanLongitude
            + 6.288774 * sin(moonAnomalyRadians)
            + 1.274027 * sin(2 * elongation - moonAnomalyRadians)
            + 0.658314 * sin(2 * elongation)
            + 0.213618 * sin(2 * moonAnomalyRadians)
            - 0.185116 * sin(sunAnomalyRadians)
            - 0.114332 * sin(2 * latitudeArgument)
            + 0.058793 * sin(2 * elongation - 2 * moonAnomalyRadians)
            + 0.057066 * sin(2 * elongation - sunAnomalyRadians - moonAnomalyRadians)
            + 0.053322 * sin(2 * elongation + moonAnomalyRadians)
            + 0.045758 * sin(2 * elongation - sunAnomalyRadians)
            - 0.040923 * sin(sunAnomalyRadians - moonAnomalyRadians)
            - 0.034720 * sin(elongation)
            - 0.030383 * sin(sunAnomalyRadians + moonAnomalyRadians)
            + 0.015327 * sin(2 * elongation - 2 * latitudeArgument)
            - 0.012528 * sin(moonAnomalyRadians + 2 * latitudeArgument)
            + 0.010980 * sin(moonAnomalyRadians - 2 * latitudeArgument)

        let latitude = 5.128122 * sin(latitudeArgument)
            + 0.280602 * sin(moonAnomalyRadians + latitudeArgument)
            + 0.277693 * sin(moonAnomalyRadians - latitudeArgument)
            + 0.173237 * sin(2 * elongation - latitudeArgument)
            + 0.055413 * sin(2 * elongation - moonAnomalyRadians + latitudeArgument)
            + 0.046271 * sin(2 * elongation - moonAnomalyRadians - latitudeArgument)
            + 0.032573 * sin(2 * elongation + latitudeArgument)
            + 0.017198 * sin(2 * moonAnomalyRadians + latitudeArgument)

        let distanceKm = 385_000.56
            - 20_905.355 * cos(moonAnomalyRadians)
            - 3_699.111 * cos(2 * elongation - moonAnomalyRadians)
            - 2_955.968 * cos(2 * elongation)
            - 569.925 * cos(2 * moonAnomalyRadians)
            + 48.888 * cos(sunAnomalyRadians)
            - 3.149 * cos(2 * latitudeArgument)
            + 246.158 * cos(2 * elongation - 2 * moonAnomalyRadians)
            - 152.138 * cos(2 * elongation - sunAnomalyRadians - moonAnomalyRadians)
            - 170.733 * cos(2 * elongation + moonAnomalyRadians)
            - 204.586 * cos(2 * elongation - sunAnomalyRadians)
            - 129.620 * cos(sunAnomalyRadians - moonAnomalyRadians)
            + 108.743 * cos(elongation)
            + 104.755 * cos(sunAnomalyRadians + moonAnomalyRadians)
            + 79.661 * cos(moonAnomalyRadians - 2 * latitudeArgument)
            + 10.321 * cos(2 * elongation - 2 * latitudeArgument)

        return MoonPosition(
            longitude: normalisedDegrees(longitude),
            latitude: latitude,
            distanceKm: distanceKm
        )
    }

    /// The Sun's apparent ecliptic longitude in degrees. Meeus ch. 25, low-precision form.
    static func sunLongitude(centuries time: Double) -> Double {
        let meanLongitude = 280.46646 + 36_000.76983 * time + 0.0003032 * time * time
        let meanAnomaly = radians(357.52911 + 35_999.05029 * time - 0.0001537 * time * time)
        let centre = (1.914602 - 0.004817 * time) * sin(meanAnomaly)
            + (0.019993 - 0.000101 * time) * sin(2 * meanAnomaly)
            + 0.000289 * sin(3 * meanAnomaly)
        return normalisedDegrees(meanLongitude + centre)
    }

    // MARK: - Frames of reference

    /// Ecliptic to equatorial, in radians. Meeus ch. 13.
    private static func equatorial(
        longitude: Double,
        latitude: Double,
        centuries: Double
    )
        -> (rightAscension: Double, declination: Double)
    {
        let obliquity = radians(23.439291 - 0.0130042 * centuries)
        let eclipticLongitude = radians(longitude)
        let eclipticLatitude = radians(latitude)
        let rightAscension = atan2(
            sin(eclipticLongitude) * cos(obliquity) - tan(eclipticLatitude) * sin(obliquity),
            cos(eclipticLongitude)
        )
        let declination = asin(
            sin(eclipticLatitude) * cos(obliquity)
                + cos(eclipticLatitude) * sin(obliquity) * sin(eclipticLongitude)
        )
        return (rightAscension < 0 ? rightAscension + 2 * .pi : rightAscension, declination)
    }

    /// Greenwich mean sidereal time in degrees. Meeus ch. 12.
    private static func greenwichSiderealDegrees(julianDay: Double) -> Double {
        normalisedDegrees(280.46061837 + 360.98564736629 * (julianDay - julianEpoch2000))
    }

    private static func julianDay(_ date: Date) -> Double {
        date.timeIntervalSince1970 / 86_400 + 2_440_587.5
    }

    /// Julian centuries since J2000.0, the argument every series above takes.
    private static func centuries(since date: Date) -> Double {
        (julianDay(date) - julianEpoch2000) / 36_525
    }

    // MARK: - Numerics

    /// Narrows a bracketed root to well under a second.
    ///
    /// 40 halvings of a 10-minute bracket lands far inside floating-point noise, and a fixed count
    /// always terminates.
    private static func bisect(from: Date, to: Date, difference: (Date) -> Double) -> Date {
        var lower = from
        var upper = to
        for _ in 0..<bisectionSteps {
            let middle = lower.addingTimeInterval(upper.timeIntervalSince(lower) / 2)
            if difference(lower) * difference(middle) <= 0 {
                upper = middle
            } else {
                lower = middle
            }
        }
        return lower.addingTimeInterval(upper.timeIntervalSince(lower) / 2)
    }

    private static func angularDiameterDegrees(distanceKm: Double) -> Double {
        degrees(2 * atan(moonRadiusKm / distanceKm))
    }

    private static func normalisedDegrees(_ value: Double) -> Double {
        let remainder = value.truncatingRemainder(dividingBy: 360)
        return remainder < 0 ? remainder + 360 : remainder
    }

    private static func radians(_ value: Double) -> Double {
        value * .pi / 180
    }

    private static func degrees(_ value: Double) -> Double {
        value * 180 / .pi
    }

    // MARK: - Constants

    /// The mean interval between new moons, in days.
    static let synodicMonth: Double = 29.530588853

    /// How far either side of a principal phase still counts as that phase.
    ///
    /// About 12 hours of elongation.
    private static let principalBandDegrees: Double = 6

    /// The altitude at which the Moon is considered to rise or set.
    ///
    /// Meeus's standard +0.125°, which nets refraction at the horizon (-34′) against the Moon's
    /// semidiameter and mean parallax (+57′).
    private static let horizonDegrees: Double = 0.125

    private static let julianEpoch2000: Double = 2_451_545.0
    private static let moonRadiusKm: Double = 1_737.4
    private static let scanStepSeconds: Double = 6 * 3_600
    private static let riseSetStepSeconds: Double = 10 * 60
    private static let riseSetSteps: Int = 144
    private static let bisectionSteps: Int = 40
}
