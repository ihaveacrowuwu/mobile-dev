import SwiftUI

/// The current condition, as an SF Symbol on a circular piece of Liquid Glass.
///
/// The direct counterpart to Android's `WeatherConditionBadge`, which uses Material 3
/// Expressive's `MaterialShapes`. The two platforms deliberately diverge here, and that
/// divergence is the point: Expressive expresses meaning through **shape**, Liquid Glass
/// through **depth and material**. Forcing one platform to imitate the other would produce
/// something that looks wrong on both.
///
/// ## Colour, and two attempts that were wrong
///
/// `.multicolor` came first: SF Symbols supplies semantically correct colours, yellow sun, blue
/// rain, rather than us hardcoding a palette. It fails contrast. Several conditions have no
/// coloured element at all: `cloud.fill`, `cloud.moon.fill` and `moon.stars.fill` render **white**,
/// invisible on a light surface. A light-mode screenshot showed hourly rows with no icon at all.
///
/// `.hierarchical` tinted with the app accent fixed contrast and lost the meaning: every condition
/// became the same blue, so the colour said nothing.
///
/// What is here now is a **palette** from ``WeatherPalette``: each condition gets its own hue,
/// drawn on a matching container that guarantees WCAG AA in both appearances. Warm for sun, cool
/// for night, blue for rain, legible *and* meaningful, which neither previous attempt managed.
struct ConditionBadge: View {
    let condition: WeatherCondition
    let isDaytime: Bool
    var size: CGFloat = 64

    /// The Dynamic Type multiplier, obtained by scaling 1. Applied to ``size`` so the badge grows
    /// with the user's text size exactly as a `.largeTitle`-relative symbol would.
    @ScaledMetric(relativeTo: .largeTitle) private var typeScale: CGFloat = 1

    private var box: CGFloat {
        size * typeScale
    }

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
            .padding(box * containerPaddingRatio)
            .background(colours.container, in: .circle)
            // Decorative: the text beside it already names the condition, so announcing
            // the symbol would repeat it for VoiceOver users.
            .accessibilityHidden(true)
    }

    /// Proportional to the symbol so the badge keeps its shape as Dynamic Type scales it.
    private let containerPaddingRatio: CGFloat = 0.28
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
