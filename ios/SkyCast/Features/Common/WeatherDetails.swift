import Foundation

/// Formats a ``Weather`` into the readings shown beneath the hero block.
///
/// Lives in `Features` rather than `Domain` because everything here is presentational: decimal
/// places, unit symbols, time format, and which shape the tile draws.
///
/// Labels are written inline, since on iOS a plain `String` literal is already a localisation key.
extension Weather {
    /// - Parameter includeDerived: adds the dew point. Off for Home, which stays a glance; on for
    ///   the detail screen, which is where someone goes precisely because the glance was not enough.
    func details(preferences: UserPreferences, includeDerived: Bool = false) -> [WeatherDetail] {
        let windUnit = preferences.windSpeedUnit
        let wind = windUnit.convertFromMetresPerSecond(windSpeedMetresPerSecond)
        // Beaufort is a force, not a speed: "5 Bft", never "5.0 Bft".
        let windText = windUnit.isWholeNumber
            ? "\(Int(wind.rounded())) \(windUnit.symbol)"
            : "\(wind.oneDecimalPlace) \(windUnit.symbol)"

        let pressureUnit = preferences.pressureUnit
        let pressure = pressureUnit.convertFromHectopascals(Double(pressureHpa))
        let pressureText = "\(pressure.formatted(places: pressureUnit.decimalPlaces)) \(pressureUnit.symbol)"

        let visibilityUnit = preferences.visibilityUnit
        let visibility = visibilityUnit.convertFromMetres(Double(visibilityMetres))

        return [
            WeatherDetail(
                label: "Humidity",
                value: "\(humidityPercent)%",
                kind: .humidity,
                // Humidity is already a percentage, so its fraction is itself.
                visual: .gauge(Double(humidityPercent) / 100)
            ),
            WeatherDetail(
                label: "Wind",
                value: windText,
                kind: .wind,
                // The direction has been in the model since the first commit and was never shown,
                // because a bar cannot draw a bearing. A compass can.
                visual: .compass(
                    degrees: Double(windDirectionDegrees),
                    cardinal: Self.cardinal(for: windDirectionDegrees)
                )
            ),
            WeatherDetail(
                label: "Pressure",
                value: pressureText,
                kind: .pressure,
                // Scaled across the range a barometer realistically covers, so the arc moves
                // meaningfully day to day.
                visual: .gauge((Double(pressureHpa) - Self.lowPressureHpa) / Self.pressureRangeHpa)
            ),
            WeatherDetail(
                label: "Visibility",
                value: "\(visibility.oneDecimalPlace) \(visibilityUnit.symbol)",
                kind: .visibility,
                // 10 km is the value OpenWeather reports for "clear", so it is effectively the top.
                visual: .gauge(Double(visibilityMetres) / Self.clearVisibilityMetres)
            ),
            WeatherDetail(
                label: "Cloud cover",
                value: "\(cloudinessPercent)%",
                kind: .cloud,
                visual: .gauge(Double(cloudinessPercent) / 100)
            ),
        ] + (includeDerived ? derivedDetails(preferences: preferences) : [])
    }

    private func derivedDetails(preferences: UserPreferences) -> [WeatherDetail] {
        let unit = preferences.temperatureUnit
        let dewPoint = unit.convertFromCelsius(dewPointCelsius)
        return [
            WeatherDetail(
                label: "Dew point",
                value: "\(Int(dewPoint.rounded()))\(unit.symbol)",
                kind: .dewPoint,
                // Measured against the air temperature: a dew point close to it is what "muggy"
                // actually means, and a nearly-full arc says so without the meteorology.
                visual: .gauge(dewPointCelsius / temperatureCelsius)
            ),
        ]
    }

    /// Everything the sun-path card needs, or `nil` where the times are unusable.
    func sunPath(now: Date = .now) -> SunPathReading? {
        let span = sunset.timeIntervalSince(sunrise)
        guard span > 0 else { return nil }
        return SunPathReading(
            progress: now.timeIntervalSince(sunrise) / span,
            sunriseLabel: localTime(sunrise),
            sunsetLabel: localTime(sunset),
            daylightLabel: daylightDuration.hoursAndMinutes,
            announcement: "Sunrise \(localTime(sunrise)), sunset \(localTime(sunset)), "
                + "\(daylightDuration.spokenDuration) of daylight"
        )
    }

    /// The 16-point compass name for a bearing, so "west-northwest" is available and not just
    /// "west".
    static func cardinal(for degrees: Int) -> String {
        let points = [
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        ]
        // Normalised first: the API is documented as 0–360, but 360 and a negative are both
        // representable, and an out-of-range index here would crash the whole screen.
        let normalised = ((degrees % 360) + 360) % 360
        let sector = Int((Double(normalised) / 22.5).rounded()) % points.count
        return points[sector]
    }

    /// Formats an instant as a wall-clock time **in the observed location's zone**, not the
    /// device's: London's sunrise is 04:49 in London whatever the phone is set to.
    ///
    /// 24-hour clock, matching Android's `HH:mm`.
    private func localTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = timeZone
        return formatter.string(from: date)
    }

    /// Roughly a strong gale: the point past which the wind indicator reads as full.
    private static let strongWindMetresPerSecond = 25.0
    private static let lowPressureHpa = 950.0
    private static let pressureRangeHpa = 130.0
    private static let clearVisibilityMetres = 10_000.0
}

/// The sun-path card's content, already formatted.
struct SunPathReading: Equatable {
    let progress: Double
    let sunriseLabel: String
    let sunsetLabel: String
    let daylightLabel: String
    let announcement: String
}

private extension TimeInterval {
    /// "14h 28m", rather than a count of seconds.
    var hoursAndMinutes: String {
        let minutes = Int(self / 60)
        return "\(minutes / 60)h \(minutes % 60)m"
    }

    /// The same duration in words, because "14h 28m" read aloud is not a sentence.
    var spokenDuration: String {
        let minutes = Int(self / 60)
        return "\(minutes / 60) hours \(minutes % 60) minutes"
    }
}

private extension Double {
    /// One decimal place, which is as much precision as these readings support.
    var oneDecimalPlace: Double {
        (self * 10).rounded() / 10
    }

    /// Formats to a fixed number of decimals. 0 gives a bare integer, not "1013.0".
    func formatted(places: Int) -> String {
        places == 0 ? "\(Int(rounded()))" : String(format: "%.\(places)f", self)
    }
}
