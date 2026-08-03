import Foundation

/// The user's settings.
///
/// Persisted in `UserDefaults` (via `SettingsStore`) rather than SwiftData, since these are a
/// handful of scalars with no relationships.
struct UserPreferences: Equatable, Sendable {
    var temperatureUnit: TemperatureUnit = .celsius
    var windSpeedUnit: WindSpeedUnit = .metresPerSecond
    var themeMode: ThemeMode = .system
}

enum TemperatureUnit: String, CaseIterable, Sendable, Identifiable {
    case celsius
    case fahrenheit

    var id: String {
        rawValue
    }

    var symbol: String {
        switch self {
        case .celsius: "°C"
        case .fahrenheit: "°F"
        }
    }

    var displayName: String {
        switch self {
        case .celsius: "Celsius (°C)"
        case .fahrenheit: "Fahrenheit (°F)"
        }
    }

    /// Converts a canonical Celsius value into this unit.
    func convertFromCelsius(_ celsius: Double) -> Double {
        switch self {
        case .celsius: celsius
        case .fahrenheit: celsius * 9 / 5 + 32
        }
    }
}

enum WindSpeedUnit: String, CaseIterable, Sendable, Identifiable {
    case metresPerSecond
    case kilometresPerHour
    case milesPerHour

    var id: String {
        rawValue
    }

    var symbol: String {
        switch self {
        case .metresPerSecond: "m/s"
        case .kilometresPerHour: "km/h"
        case .milesPerHour: "mph"
        }
    }

    func convertFromMetresPerSecond(_ metresPerSecond: Double) -> Double {
        switch self {
        case .metresPerSecond: metresPerSecond
        case .kilometresPerHour: metresPerSecond * 3.6
        case .milesPerHour: metresPerSecond * 2.236_936
        }
    }
}

enum ThemeMode: String, CaseIterable, Sendable, Identifiable {
    case system
    case light
    case dark

    var id: String {
        rawValue
    }

    var displayName: String {
        switch self {
        case .system: "Follow system"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}
