import Foundation

/// Formats a ``Weather`` into the secondary readings shown under the hero block.
///
/// Lives in `Features` rather than `Domain` because every decision here is presentational: how
/// many decimal places, which unit symbol, what time format.
///
/// Unlike the Android counterpart (`ui/common/WeatherDetails.kt`), the labels are written inline
/// rather than passed in. Android has to hoist them because `stringResource` needs a `Context` and
/// this function deliberately has none; on iOS a plain `String` literal is already a localisation
/// key, so the indirection would buy nothing.
extension Weather {
    func details(preferences: UserPreferences) -> [WeatherDetail] {
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
                fraction: Double(humidityPercent) / 100
            ),
            WeatherDetail(
                label: "Wind",
                value: windText,
                kind: .wind,
                fraction: windSpeedMetresPerSecond / Self.strongWindMetresPerSecond
            ),
            WeatherDetail(
                label: "Pressure",
                value: pressureText,
                kind: .pressure,
                // Scaled across the range a barometer realistically covers, so the indicator moves
                // meaningfully instead of sitting in the same spot every day.
                fraction: (Double(pressureHpa) - Self.lowPressureHpa) / Self.pressureRangeHpa
            ),
            WeatherDetail(
                label: "Visibility",
                value: "\(visibility.oneDecimalPlace) \(visibilityUnit.symbol)",
                kind: .visibility,
                // 10 km is the value OpenWeather reports for "clear", so it is effectively the top.
                fraction: Double(visibilityMetres) / Self.clearVisibilityMetres
            ),
            WeatherDetail(label: "Sunrise", value: localTime(sunrise), kind: .sunrise),
            WeatherDetail(label: "Sunset", value: localTime(sunset), kind: .sunset),
        ]
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
