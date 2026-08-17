import SwiftUI

/// Weather-semantic colours: a hue per condition, so the colour carries meaning rather than
/// hierarchy.
///
/// SF Symbol `.multicolor` rendering fails contrast, since `cloud.fill`, `cloud.moon.fill` and
/// `moon.stars.fill` render white and are invisible on a light surface. `.hierarchical` tinted with
/// one accent makes every condition the same colour.
///
/// Each pair here is a container plus the colour drawn on it, chosen to clear WCAG AA at the sizes
/// used here in **both** appearances. `Color(light:dark:)` resolves per trait collection rather
/// than per launch, so a mid-session appearance change is picked up.
///
/// The Android counterpart is `core/designsystem/theme/WeatherPalette.kt`, with the same hues.
enum WeatherPalette {
    // MARK: - Conditions

    static let sunContainer = Color(light: 0xFFE7AE, dark: 0x4A3812)
    static let onSunContainer = Color(light: 0x7A4E00, dark: 0xFFD98A)
    static let moonContainer = Color(light: 0xDCE1F5, dark: 0x232949)
    static let onMoonContainer = Color(light: 0x2B3768, dark: 0xC2CAEE)
    static let cloudContainer = Color(light: 0xE5E9F0, dark: 0x2A303A)
    static let onCloudContainer = Color(light: 0x3F4A5C, dark: 0xC8D1E0)
    static let rainContainer = Color(light: 0xD3E5FB, dark: 0x113152)
    static let onRainContainer = Color(light: 0x0F4478, dark: 0xA8CCF2)
    static let drizzleContainer = Color(light: 0xDBEFFA, dark: 0x0F3F4E)
    static let onDrizzleContainer = Color(light: 0x115B79, dark: 0xA5DBF0)
    static let thunderContainer = Color(light: 0xE8DDFB, dark: 0x32225B)
    static let onThunderContainer = Color(light: 0x452A80, dark: 0xCCBAF5)
    static let snowContainer = Color(light: 0xE2F2F6, dark: 0x113945)
    static let onSnowContainer = Color(light: 0x0F5165, dark: 0xADDFEB)
    static let mistContainer = Color(light: 0xE4E8E5, dark: 0x292F2B)
    static let onMistContainer = Color(light: 0x44504B, dark: 0xC2CBC5)

    // MARK: - Metrics

    static let humidity = Color(light: 0x1B6EC2, dark: 0x7FB6EE)
    static let wind = Color(light: 0x0F7A72, dark: 0x62C8BF)
    static let pressure = Color(light: 0x6A4CA8, dark: 0xB9A2EE)
    static let visibility = Color(light: 0x0E7490, dark: 0x6FC5DE)
    static let sunrise = Color(light: 0xC9741A, dark: 0xF0B267)
    static let sunset = Color(light: 0xB4501F, dark: 0xE58A63)

    // MARK: - Lookup

    /// Container and content colours for a condition.
    ///
    /// Clear and cloudy skies read differently at night, which is the same distinction the icon
    /// itself makes, so the colour must not stay warm after dark.
    static func colours(for condition: WeatherCondition, isDaytime: Bool) -> (container: Color, content: Color) {
        switch condition {
        case .clear:
            isDaytime ? (sunContainer, onSunContainer) : (moonContainer, onMoonContainer)
        case .clouds:
            isDaytime ? (cloudContainer, onCloudContainer) : (moonContainer, onMoonContainer)
        case .rain: (rainContainer, onRainContainer)
        case .drizzle: (drizzleContainer, onDrizzleContainer)
        case .thunderstorm: (thunderContainer, onThunderContainer)
        case .snow: (snowContainer, onSnowContainer)
        case .mist: (mistContainer, onMistContainer)
        case .unknown: (Color(.secondarySystemFill), Color.secondary)
        }
    }
}

extension Color {
    /// Builds a colour from two hex values, resolved against the current appearance.
    ///
    /// `UIColor(dynamicProvider:)` rather than two static colours picked once: it re-resolves
    /// whenever the trait collection changes, so switching to dark mode mid-session updates
    /// everything without a relaunch.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
