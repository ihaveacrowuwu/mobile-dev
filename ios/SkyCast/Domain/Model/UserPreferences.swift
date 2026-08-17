import Foundation

/// The user's settings.
///
/// Persisted in `UserDefaults` (via `SettingsStore`) rather than SwiftData, since these are a
/// handful of scalars with no relationships.
struct UserPreferences: Equatable, Sendable {
    var temperatureUnit: TemperatureUnit = .celsius
    var windSpeedUnit: WindSpeedUnit = .metresPerSecond
    var pressureUnit: PressureUnit = .hectopascals
    var visibilityUnit: VisibilityUnit = .kilometres
    var themeMode: ThemeMode = .system
}

/// A displayable unit.
///
/// Every conforming type converts **from the canonical value** the domain stores, Celsius, metres
/// per second, hectopascals, metres, rather than between arbitrary pairs. One direction of
/// conversion per unit means there is no lattice of conversions to get wrong, and it is what lets a
/// settings change re-render from cache with no network call.
///
/// Mirrors `DisplayUnit` on Android.
protocol DisplayUnit: CaseIterable, Identifiable, Hashable, Sendable {
    /// Appended to the value, e.g. "°C" or "kt".
    var symbol: String { get }

    /// Shown in Settings, e.g. "Knots (kt)".
    var displayName: String { get }
}

enum TemperatureUnit: String, DisplayUnit {
    case celsius
    case fahrenheit

    /// Absolute temperature. Not a practical choice for deciding whether to take a coat, and that is
    /// rather the point of offering it, it costs one line and it is correct.
    case kelvin

    var id: String {
        rawValue
    }

    var symbol: String {
        switch self {
        case .celsius: "°C"
        case .fahrenheit: "°F"
        case .kelvin: " K"
        }
    }

    var displayName: String {
        switch self {
        case .celsius: "Celsius (°C)"
        case .fahrenheit: "Fahrenheit (°F)"
        case .kelvin: "Kelvin (K)"
        }
    }

    /// Converts a canonical Celsius value into this unit.
    func convertFromCelsius(_ celsius: Double) -> Double {
        switch self {
        case .celsius: celsius
        case .fahrenheit: celsius * 9 / 5 + 32
        case .kelvin: celsius + Self.absoluteZeroCelsius
        }
    }

    /// 0 °C in kelvin.
    private static let absoluteZeroCelsius = 273.15
}

enum WindSpeedUnit: String, DisplayUnit {
    case metresPerSecond
    case kilometresPerHour
    case milesPerHour

    /// Nautical miles per hour, what aviation and sailing actually use, and what every METAR
    /// reports. One knot is exactly 1852 m/h by definition of the nautical mile.
    case knots

    /// The Beaufort scale: a force number rather than a speed, describing observable effects at sea
    /// and on land.
    case beaufort

    var id: String {
        rawValue
    }

    var symbol: String {
        switch self {
        case .metresPerSecond: "m/s"
        case .kilometresPerHour: "km/h"
        case .milesPerHour: "mph"
        case .knots: "kt"
        case .beaufort: "Bft"
        }
    }

    var displayName: String {
        switch self {
        case .metresPerSecond: "Metres per second (m/s)"
        case .kilometresPerHour: "Kilometres per hour (km/h)"
        case .milesPerHour: "Miles per hour (mph)"
        case .knots: "Knots (kt)"
        case .beaufort: "Beaufort scale"
        }
    }

    func convertFromMetresPerSecond(_ metresPerSecond: Double) -> Double {
        switch self {
        case .metresPerSecond: metresPerSecond
        case .kilometresPerHour: metresPerSecond * 3.6
        case .milesPerHour: metresPerSecond * 2.236_936
        case .knots: metresPerSecond * Self.metresPerSecondInKnots
        case .beaufort: Double(Self.beaufortForce(metresPerSecond))
        }
    }

    /// Beaufort is a scale of whole numbers, so a decimal place would be meaningless.
    var isWholeNumber: Bool {
        self == .beaufort
    }

    /// 3600 / 1852, from the definition of the nautical mile.
    private static let metresPerSecondInKnots = 1.943_844

    /// Upper bound of each Beaufort force in m/s, from the standard scale. Force 12 has no upper
    /// bound, so anything above the last entry is a hurricane.
    private static let beaufortUpperBounds: [Double] = [
        0.5, 1.5, 3.3, 5.5, 7.9, 10.7, 13.8, 17.1, 20.7, 24.4, 28.4, 32.6,
    ]

    private static func beaufortForce(_ metresPerSecond: Double) -> Int {
        beaufortUpperBounds.firstIndex { metresPerSecond < $0 } ?? beaufortUpperBounds.count
    }
}

/// Atmospheric pressure.
///
/// Hectopascals and millibars are numerically identical, 1 hPa = 1 mbar exactly, so only one is
/// offered, labelled with both names rather than pretending they are a choice.
enum PressureUnit: String, DisplayUnit {
    case hectopascals

    /// Inches of mercury: the altimeter setting in the United States, Canada and Japan, and the
    /// reason a pilot's altimeter reads 29.92 on a standard day.
    case inchesOfMercury

    /// Millimetres of mercury, still used for pressure in parts of Europe and in medicine.
    case millimetresOfMercury

    var id: String {
        rawValue
    }

    var symbol: String {
        switch self {
        case .hectopascals: "hPa"
        case .inchesOfMercury: "inHg"
        case .millimetresOfMercury: "mmHg"
        }
    }

    var displayName: String {
        switch self {
        case .hectopascals: "Hectopascals (hPa / mbar)"
        case .inchesOfMercury: "Inches of mercury (inHg)"
        case .millimetresOfMercury: "Millimetres of mercury (mmHg)"
        }
    }

    func convertFromHectopascals(_ hectopascals: Double) -> Double {
        switch self {
        case .hectopascals: hectopascals
        case .inchesOfMercury: hectopascals * 0.029_529_98
        case .millimetresOfMercury: hectopascals * 0.750_062
        }
    }

    /// inHg is conventionally quoted to two decimals (29.92); the others to none.
    var decimalPlaces: Int {
        self == .inchesOfMercury ? 2 : 0
    }
}

/// Visibility.
///
/// Aviation reports visibility in statute miles in the United States and in metres elsewhere;
/// nautical miles are included because they are the unit everything else in a cockpit uses.
enum VisibilityUnit: String, DisplayUnit {
    case kilometres
    case miles
    case nauticalMiles

    var id: String {
        rawValue
    }

    var symbol: String {
        switch self {
        case .kilometres: "km"
        case .miles: "mi"
        case .nauticalMiles: "NM"
        }
    }

    var displayName: String {
        switch self {
        case .kilometres: "Kilometres (km)"
        case .miles: "Statute miles (mi)"
        case .nauticalMiles: "Nautical miles (NM)"
        }
    }

    func convertFromMetres(_ metres: Double) -> Double {
        switch self {
        case .kilometres: metres / 1_000
        case .miles: metres / 1_609.344
        // Exact by definition.
        case .nauticalMiles: metres / 1_852
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
