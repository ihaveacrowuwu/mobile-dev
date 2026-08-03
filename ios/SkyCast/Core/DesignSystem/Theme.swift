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
/// Everything is built on the system text styles, so text scales with the user's Dynamic
/// Type setting.
extension Font {
    /// The one oversized style, for the single hero temperature reading.
    ///
    /// `.system(size:weight:design:)` alone would NOT scale, so it is wrapped in a
    /// relative text style, this is the whole reason the helper exists rather than
    /// callers writing `.font(.system(size: 88))`.
    static var skyHeroTemperature: Font {
        .system(size: 88, weight: .thin, design: .rounded)
    }
}
