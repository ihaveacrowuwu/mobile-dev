import SwiftUI

/// The current condition, as a bare SF Symbol in the condition's own colour.
///
/// The counterpart to Android's `WeatherConditionBadge`, which uses Material 3 Expressive's
/// `MaterialShapes`.
///
/// Colours come from ``WeatherPalette``: each condition gets its own hue, chosen to clear WCAG AA
/// against the page in both appearances. Warm for sun, cool for night, blue for rain.
///
/// The **square frame is load-bearing**. Sized by font, each symbol's own proportions would decide
/// its footprint: `sun.max.fill` is square where `cloud.moon.fill` is wide and short, so without a
/// fixed frame the hourly temperatures would not share a baseline.
struct ConditionBadge: View {
    let condition: WeatherCondition
    let isDaytime: Bool
    var size: CGFloat = 64

    /// The Dynamic Type multiplier, obtained by scaling 1. Applied to ``size`` so the badge grows
    /// with the user's text size exactly as a `.largeTitle`-relative symbol would.
    @ScaledMetric(relativeTo: .largeTitle) private var typeScale: CGFloat = 1

    /// Sized so the symbol carries the tile on its own, without a container around it.
    private var box: CGFloat {
        size * typeScale * Self.bareSymbolScale
    }

    private static let bareSymbolScale: CGFloat = 1.25

    private var colours: (container: Color, content: Color) {
        WeatherPalette.colours(for: condition, isDaytime: isDaytime)
    }

    var body: some View {
        // Fitted into a **square**, not sized by the glyph. Set as a font size, each symbol's own
        // proportions decided the badge's: `sun.max.fill` is square where `cloud.moon.fill` is
        // wide and short, so the circle around them came out a different size per condition. That
        // moved everything beneath it, a clear Malé sat visibly lower on the page than an
        // overcast London, and the hourly temperatures never shared a baseline.
        Image(systemName: condition.symbolName(isDaytime: isDaytime))
            .resizable()
            .scaledToFit()
            .frame(width: box, height: box)
            .symbolRenderingMode(.hierarchical)
            .foregroundStyle(colours.content)
            // Decorative: the text beside it already names the condition, so announcing
            // the symbol would repeat it for VoiceOver users.
            .accessibilityHidden(true)
    }
}

#Preview("Every condition") {
    ScrollView {
        // Grouped so adjacent badges blend as one material rather than showing seams.
        SkyGlassGroup(spacing: Spacing.lg) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))], spacing: Spacing.lg) {
                ForEach(WeatherCondition.allCases, id: \.rawValue) { condition in
                    VStack(spacing: Spacing.sm) {
                        ConditionBadge(condition: condition, isDaytime: true, size: 48)
                        Text(String(describing: condition))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(Spacing.md)
        }
    }
}
