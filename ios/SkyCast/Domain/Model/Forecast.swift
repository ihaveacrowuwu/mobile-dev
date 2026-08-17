import Foundation

/// A multi-day forecast for one location, already grouped into days.
///
/// OpenWeather's free forecast endpoint returns a flat list of 3-hourly readings; the
/// grouping happens in `WeatherMapper` so no view ever does date arithmetic.
struct Forecast: Equatable, Sendable {
    let locationID: Int64
    let locationName: String
    let days: [ForecastDay]
    let cachedAt: Date
    /// See ``Weather/timeZoneOffsetSeconds``.
    ///
    /// Also what keeps ``days`` stable: the grouping into calendar days must use the same zone
    /// whether the forecast just arrived from the API or was rebuilt from cache, or a day's
    /// boundary, and therefore the identity of a day-detail route, shifts between the two.
    let timeZoneOffsetSeconds: Int

    /// The forecast location's time zone. Falls back to UTC, as ``Weather/timeZone`` does.
    var timeZone: TimeZone {
        TimeZone(secondsFromGMT: timeZoneOffsetSeconds) ?? .gmt
    }

    func isStale(now: Date, ttl: TimeInterval = Forecast.forecastTTL) -> Bool {
        cachedAt.addingTimeInterval(ttl) < now
    }

    /// Forecasts change slowly; a 3-hour TTL keeps well inside the free API quota.
    static let forecastTTL: TimeInterval = 3 * 60 * 60
}

/// One calendar day of the forecast, with its 3-hourly readings kept for the detail screen.
struct ForecastDay: Equatable, Sendable, Identifiable {
    let date: Date
    let condition: WeatherCondition
    let description: String
    let iconCode: String
    let minTemperatureCelsius: Double
    let maxTemperatureCelsius: Double
    let precipitationProbability: Double
    let hourly: [HourlyForecast]

    /// Stable across re-fetches because it is derived from the calendar day itself.
    var id: Date {
        date
    }
}

/// A single 3-hourly reading.
struct HourlyForecast: Equatable, Sendable, Identifiable {
    let time: Date
    let condition: WeatherCondition
    let iconCode: String
    let temperatureCelsius: Double
    let precipitationProbability: Double
    let windSpeedMetresPerSecond: Double

    var id: Date {
        time
    }
}
