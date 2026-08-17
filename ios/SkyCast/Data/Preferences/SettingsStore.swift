import Foundation
import Observation

/// User settings, backed by `UserDefaults`, the iOS counterpart to Android's DataStore.
///
/// `@Observable` rather than `@AppStorage`: `@AppStorage` only works inside a `View`, which
/// would force settings knowledge into every screen that needs a unit. Exposing one
/// observable object instead means the domain layer owns the contract, views merely read
/// it, and changing a unit re-renders every screen from cache with no network call.
///
/// `@MainActor` because it drives SwiftUI directly and every caller is already there.
@MainActor
@Observable
final class SettingsStore: SettingsRepository {
    private let defaults: UserDefaults

    /// The single source of truth. Views observe this; nothing writes it except the
    /// setters below, which persist first and then update it.
    private(set) var preferences: UserPreferences

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        preferences = Self.load(from: defaults)
    }

    func setTemperatureUnit(_ unit: TemperatureUnit) {
        preferences.temperatureUnit = unit
        defaults.set(unit.rawValue, forKey: Keys.temperatureUnit)
    }

    func setWindSpeedUnit(_ unit: WindSpeedUnit) {
        preferences.windSpeedUnit = unit
        defaults.set(unit.rawValue, forKey: Keys.windSpeedUnit)
    }

    func setPressureUnit(_ unit: PressureUnit) {
        preferences.pressureUnit = unit
        defaults.set(unit.rawValue, forKey: Keys.pressureUnit)
    }

    func setVisibilityUnit(_ unit: VisibilityUnit) {
        preferences.visibilityUnit = unit
        defaults.set(unit.rawValue, forKey: Keys.visibilityUnit)
    }

    func setThemeMode(_ mode: ThemeMode) {
        preferences.themeMode = mode
        defaults.set(mode.rawValue, forKey: Keys.themeMode)
    }

    func reset() {
        for key in Keys.all {
            defaults.removeObject(forKey: key)
        }
        preferences = UserPreferences()
    }

    // MARK: - Internals

    /// Reads stored values, falling back to defaults when a key is absent or is not a valid case,
    /// which happens when an enum case is renamed in a later app version.
    private static func load(from defaults: UserDefaults) -> UserPreferences {
        UserPreferences(
            temperatureUnit: defaults.string(forKey: Keys.temperatureUnit)
                .flatMap(TemperatureUnit.init(rawValue:)) ?? .celsius,
            windSpeedUnit: defaults.string(forKey: Keys.windSpeedUnit)
                .flatMap(WindSpeedUnit.init(rawValue:)) ?? .metresPerSecond,
            pressureUnit: defaults.string(forKey: Keys.pressureUnit)
                .flatMap(PressureUnit.init(rawValue:)) ?? .hectopascals,
            visibilityUnit: defaults.string(forKey: Keys.visibilityUnit)
                .flatMap(VisibilityUnit.init(rawValue:)) ?? .kilometres,
            themeMode: defaults.string(forKey: Keys.themeMode)
                .flatMap(ThemeMode.init(rawValue:)) ?? .system
        )
    }

    private enum Keys {
        static let temperatureUnit = "settings.temperatureUnit"
        static let windSpeedUnit = "settings.windSpeedUnit"
        static let pressureUnit = "settings.pressureUnit"
        static let visibilityUnit = "settings.visibilityUnit"
        static let themeMode = "settings.themeMode"

        static let all = [temperatureUnit, windSpeedUnit, pressureUnit, visibilityUnit, themeMode]
    }
}
