import Foundation

/// Current weather conditions for a single location.
///
/// A **domain** model: no SwiftUI, no SwiftData, no networking types. Temperatures are
/// always stored in Celsius and converted for display, so changing units re-renders from
/// cache with no network call.
///
/// Mirrors `Weather.kt` on Android field for field, the parity is deliberate, so the
/// platform comparison in `docs/reflection.md` is about the platforms rather than about
/// two different designs.
struct Weather: Equatable, Sendable, Identifiable {
    let locationID: Int64
    let locationName: String
    let condition: WeatherCondition
    let description: String
    let iconCode: String
    let temperatureCelsius: Double
    let feelsLikeCelsius: Double
    let minTemperatureCelsius: Double
    let maxTemperatureCelsius: Double
    let humidityPercent: Int
    let pressureHpa: Int
    let windSpeedMetresPerSecond: Double
    let windDirectionDegrees: Int
    let cloudinessPercent: Int
    let visibilityMetres: Int
    let sunrise: Date
    let sunset: Date
    let observedAt: Date
    /// When this record was written to the local cache. Drives staleness.
    let cachedAt: Date

    var id: Int64 {
        locationID
    }

    /// True while the sun is up at the observed location, which picks the day/night art.
    var isDaytime: Bool {
        observedAt >= sunrise && observedAt <= sunset
    }

    /// Whether the cached copy is older than `ttl` and should be refreshed.
    ///
    /// Staleness is a *presentation* concern, not an error: stale data is still shown.
    func isStale(now: Date, ttl: TimeInterval = Weather.currentWeatherTTL) -> Bool {
        cachedAt.addingTimeInterval(ttl) < now
    }

    /// OpenWeather refreshes station data roughly every 10 minutes.
    static let currentWeatherTTL: TimeInterval = 10 * 60
}

/// The condition groups SkyCast renders distinct artwork for.
///
/// A closed enum rather than the raw OpenWeather integer, so `switch` exhaustiveness
/// guarantees every branch of the UI handles every condition.
enum WeatherCondition: Int, CaseIterable, Sendable {
    case clear
    case clouds
    case rain
    case drizzle
    case thunderstorm
    case snow
    case mist
    case unknown

    /// Maps an OpenWeather condition id.
    /// See https://openweathermap.org/weather-conditions for the id ranges.
    static func fromOpenWeatherID(_ id: Int) -> WeatherCondition {
        switch id {
        case 200...232: .thunderstorm
        case 300...321: .drizzle
        case 500...531: .rain
        case 600...622: .snow
        case 700...781: .mist
        case 800: .clear
        case 801...804: .clouds
        default: .unknown
        }
    }

    /// SF Symbol for this condition, varying by time of day where it matters.
    func symbolName(isDaytime: Bool) -> String {
        switch self {
        case .clear: isDaytime ? "sun.max.fill" : "moon.stars.fill"
        case .clouds: isDaytime ? "cloud.sun.fill" : "cloud.moon.fill"
        case .rain: "cloud.rain.fill"
        case .drizzle: "cloud.drizzle.fill"
        case .thunderstorm: "cloud.bolt.rain.fill"
        case .snow: "cloud.snow.fill"
        case .mist: "cloud.fog.fill"
        case .unknown: "questionmark.circle"
        }
    }
}
