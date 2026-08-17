import Foundation

/// Formats a ``Weather`` into the secondary readings shown under the hero block.
///
/// Lives in `Features` rather than `Domain` because every decision here is presentational: how
/// many decimal places, which unit symbol, what time format.
///
/// Unlike the Android counterpart (`ui/common/WeatherDetails.kt`), the labels are written inline
/// rather than passed in. Android has to hoist them because `stringResource` needs a `Context`
/// and this function deliberately has none; on iOS a plain `String` literal is already a
/// localisation key, so the indirection would buy nothing.
extension Weather {
    func details(windUnit: WindSpeedUnit) -> [WeatherDetail] {
        let wind = windUnit.convertFromMetresPerSecond(windSpeedMetresPerSecond)
        return [
            WeatherDetail(label: "Humidity", value: "\(humidityPercent)%"),
            WeatherDetail(label: "Wind", value: "\(wind.oneDecimalPlace) \(windUnit.symbol)"),
            WeatherDetail(label: "Pressure", value: "\(pressureHpa) hPa"),
            // The API reports metres; people read kilometres.
            WeatherDetail(
                label: "Visibility",
                value: "\((Double(visibilityMetres) / metresPerKilometre).oneDecimalPlace) km"
            ),
            WeatherDetail(label: "Sunrise", value: localTime(sunrise)),
            WeatherDetail(label: "Sunset", value: localTime(sunset)),
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
}

private let metresPerKilometre = 1_000.0

private extension Double {
    /// One decimal place, which is as much precision as these readings support.
    var oneDecimalPlace: Double {
        (self * 10).rounded() / 10
    }
}
