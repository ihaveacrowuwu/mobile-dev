import SwiftUI

/// Semantic colours.
///
/// Views use these names, never literal colours, which is what makes dark mode work without
/// touching a single screen. Where a system colour already carries the right meaning it is used
/// directly: `.primary`, `.secondary` and the grouped-background colours adapt to light/dark and to
/// increased-contrast settings.
extension Color {
    /// Brand accent, a clear-sky blue. Defined in Assets.xcassets so it adapts.
    static let skyAccent = Color("AccentColor")

    /// Page background. The grouped variant is what iOS uses for settings-style lists.
    static let skyBackground = Color(.systemGroupedBackground)

    /// Card and row surfaces sitting on `skyBackground`.
    static let skySurface = Color(.secondarySystemGroupedBackground)

    /// Warning tint for the stale-data banner.
    static let skyWarning = Color(.systemOrange)

    /// Error tint.
    static let skyError = Color(.systemRed)
}

extension ThemeMode {
    /// The SwiftUI `ColorScheme` to force, or `nil` to follow the system.
    ///
    /// `nil` is meaningful here: returning a concrete scheme for `.system` would override
    /// the user's device-wide setting instead of following it.
    var preferredColorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

/// Typography helpers.
///
/// Everything is built on the system text styles, so text scales with the user's Dynamic Type
/// setting.
enum SkyTypography {
    /// Base point size for the single hero temperature reading.
    ///
    /// Must be used with `@ScaledMetric(relativeTo: .largeTitle)`, see
    /// ``ScaledHeroTemperature``. `Font.system(size:)` alone does **not** respond to Dynamic
    /// Type, which is why there is no plain `Font` extension for this.
    static let heroTemperatureBaseSize: CGFloat = 88

    /// Base point size for the large illustrative symbols in the error and empty states.
    static let stateSymbolBaseSize: CGFloat = 56
}

/// The hero temperature, at a size that scales with Dynamic Type.
///
/// `@ScaledMetric` is the only correct way to use a specific point size on iOS: it multiplies
/// the base value by the user's current text-size scale factor, relative to a named text
/// style. A bare `.font(.system(size: 88))` would stay 88pt at every accessibility setting.
struct ScaledHeroTemperature: View {
    let text: String

    @ScaledMetric(relativeTo: .largeTitle) private var size: CGFloat = SkyTypography.heroTemperatureBaseSize

    var body: some View {
        Text(text)
            .font(.system(size: size, weight: .thin, design: .rounded))
            // Stops the layout jumping as the number changes width between refreshes.
            .monospacedDigit()
            // Animates the digits themselves rather than cross-fading the whole label.
            .contentTransition(.numericText())
    }
}

/// A large illustrative SF Symbol that scales with Dynamic Type.
///
/// Apple's guidance is that symbols scale with the text beside them. A fixed-size symbol next
/// to scaled text looks wrong at large accessibility sizes.
struct ScaledSymbol: View {
    private let systemName: String

    /// Initialised via the property-wrapper backing store because the scale factor depends
    /// on `baseSize` and `relativeTo`, which are only known at init.
    @ScaledMetric private var size: CGFloat

    init(
        _ systemName: String,
        baseSize: CGFloat = SkyTypography.stateSymbolBaseSize,
        relativeTo: Font.TextStyle = .largeTitle
    ) {
        self.systemName = systemName
        _size = ScaledMetric(wrappedValue: baseSize, relativeTo: relativeTo)
    }

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: size))
    }
}
