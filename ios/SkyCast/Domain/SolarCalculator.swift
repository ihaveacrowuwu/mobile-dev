import Foundation

/// The light, as photographers reckon it.
///
/// The API already gives sunrise and sunset. What it does not give, and what people actually plan an evening
/// around, is the hour when the light goes gold, and the short window after it when everything turns blue.
/// Both are defined by the sun's **altitude**, not by the clock:
///
/// | Window | Sun's altitude |
/// | --- | --- |
/// | Golden hour | +6° down to −4° |
/// | Blue hour | −4° down to −6° |
/// | Civil twilight ends | −6° |
///
/// Those angles are why the "hour" is not an hour: it is twenty minutes in the tropics and most of an evening
/// in northern Norway in summer, and only a calculation can say which.
///
/// Computed on the device for the same reasons as ``MoonCalculator``, deterministic, keyless, offline, exact
/// for any date. The solar position is the standard low-precision form (Meeus ch. 25 and 13), which
/// reproduces London's published sunrise and sunset for 19 August 2026 (05:52 and 20:14 BST) to the minute.
///
/// The Kotlin twin is `SolarCalculator.kt`.
enum SolarCalculator {
    /// Golden hour begins as the sun drops past this altitude, and ends at ``goldenHourEndDegrees``.
    static let goldenHourStartDegrees = 6.0

    /// Golden hour ends and blue hour begins here, a little below the horizon.
    static let goldenHourEndDegrees = -4.0

    /// Blue hour ends here, which is also the end of civil twilight.
    static let blueHourEndDegrees = -6.0

    /// The evening light for the local day containing `date`.
    ///
    /// `nil` when the sun never reaches these altitudes that day, such as a polar summer or
    /// winter.
    static func eveningLight(
        on date: Date,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone
    )
        -> GoldenHour?
    {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let startOfDay = calendar.startOfDay(for: date)

        // Descending crossings only: the sun passes +6° twice a day, and the evening one is the second.
        guard let goldenStart = lastDescendingCrossing(
            from: startOfDay, target: goldenHourStartDegrees, latitude: latitude, longitude: longitude
        ),
            let goldenEnd = lastDescendingCrossing(
                from: startOfDay, target: goldenHourEndDegrees, latitude: latitude, longitude: longitude
            ),
            let blueEnd = lastDescendingCrossing(
                from: startOfDay, target: blueHourEndDegrees, latitude: latitude, longitude: longitude
            )
        else { return nil }

        return GoldenHour(goldenStart: goldenStart, goldenEnd: goldenEnd, blueEnd: blueEnd)
    }

    /// The sun's altitude above the horizon, in degrees.
    static func altitudeDegrees(at date: Date, latitude: Double, longitude: Double) -> Double {
        let days = julianDay(date) - julianEpoch2000
        let meanLongitude = (280.460 + 0.9856474 * days).truncatingRemainder(dividingBy: 360)
        let meanAnomaly = radians((357.528 + 0.9856003 * days).truncatingRemainder(dividingBy: 360))
        let eclipticLongitude = radians(
            meanLongitude + equationOfCentre1 * sin(meanAnomaly) + equationOfCentre2 * sin(2 * meanAnomaly)
        )
        let obliquity = radians(obliquityDegrees)

        let rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
        let declination = asin(sin(obliquity) * sin(eclipticLongitude))
        let hourAngle = radians(
            (greenwichSiderealDegrees(daysSinceEpoch: days) + longitude).truncatingRemainder(dividingBy: 360)
        ) - rightAscension
        let observerLatitude = radians(latitude)

        let sine = sin(observerLatitude) * sin(declination)
            + cos(observerLatitude) * cos(declination) * cos(hourAngle)
        return degrees(asin(min(max(sine, -1), 1)))
    }

    /// The last time in the day that the sun sinks past `target`, or `nil` if it never does.
    ///
    /// Descending specifically: every one of these altitudes is crossed twice, once climbing in the morning
    /// and once falling in the evening, and pairing a morning crossing with an evening one would produce a
    /// golden "hour" that lasted all day.
    private static func lastDescendingCrossing(
        from startOfDay: Date,
        target: Double,
        latitude: Double,
        longitude: Double
    )
        -> Date?
    {
        func difference(_ at: Date) -> Double {
            altitudeDegrees(at: at, latitude: latitude, longitude: longitude) - target
        }

        var found: Date?
        var previousTime = startOfDay
        var previous = difference(previousTime)

        for step in 1...stepsPerDay {
            let current = startOfDay.addingTimeInterval(Double(step) * stepSeconds)
            let value = difference(current)
            if previous > 0, value <= 0 {
                found = bisect(from: previousTime, to: current, difference: difference)
            }
            previousTime = current
            previous = value
        }
        return found
    }

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

    private static func greenwichSiderealDegrees(daysSinceEpoch: Double) -> Double {
        let value = (280.46061837 + 360.98564736629 * daysSinceEpoch).truncatingRemainder(dividingBy: 360)
        return value < 0 ? value + 360 : value
    }

    private static func julianDay(_ date: Date) -> Double {
        date.timeIntervalSince1970 / 86_400 + 2_440_587.5
    }

    private static func radians(_ value: Double) -> Double {
        value * .pi / 180
    }

    private static func degrees(_ value: Double) -> Double {
        value * 180 / .pi
    }

    private static let julianEpoch2000 = 2_451_545.0
    private static let obliquityDegrees = 23.439
    private static let equationOfCentre1 = 1.915
    private static let equationOfCentre2 = 0.020

    /// A minute is fine here: every window this finds is minutes long at its shortest.
    private static let stepSeconds: Double = 60
    private static let stepsPerDay = 1_440
    private static let bisectionSteps = 30
}

/// The evening's light, as three instants.
///
/// Golden hour runs from ``goldenStart`` to ``goldenEnd``; blue hour picks up there and ends at ``blueEnd``,
/// which is also the end of civil twilight.
struct GoldenHour: Equatable, Sendable {
    let goldenStart: Date
    let goldenEnd: Date
    let blueEnd: Date

    var goldenDuration: TimeInterval {
        goldenEnd.timeIntervalSince(goldenStart)
    }

    var blueDuration: TimeInterval {
        blueEnd.timeIntervalSince(goldenEnd)
    }

    /// Whether `date` falls inside the golden window.
    func isGolden(at date: Date) -> Bool {
        date >= goldenStart && date < goldenEnd
    }

    /// Whether `date` falls inside the blue window.
    func isBlue(at date: Date) -> Bool {
        date >= goldenEnd && date < blueEnd
    }

    /// Where `date` sits across the whole golden-plus-blue span, or `nil` outside it.
    ///
    /// `nil` rather than a clamped 0 or 1: a marker parked at an end would say "it is happening" all
    /// afternoon and all night, which is the opposite of what the band is for.
    func progress(at date: Date) -> Double? {
        guard date >= goldenStart, date <= blueEnd else { return nil }
        let total = blueEnd.timeIntervalSince(goldenStart)
        guard total > 0 else { return nil }
        return date.timeIntervalSince(goldenStart) / total
    }
}
