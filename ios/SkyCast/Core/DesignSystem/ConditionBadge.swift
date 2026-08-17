import SwiftUI

/// The current condition, as an SF Symbol on a circular piece of Liquid Glass.
///
/// The direct counterpart to Android's `WeatherConditionBadge`, which uses Material 3
/// Expressive's `MaterialShapes`. The two platforms deliberately diverge here, and that
/// divergence is the point: Expressive expresses meaning through **shape**, Liquid Glass
/// through **depth and material**. Forcing one platform to imitate the other would produce
/// something that looks wrong on both.
///
/// ## Rendering mode, and why it is not `.multicolor`
///
/// `.multicolor` was the first choice: SF Symbols then supplies semantically correct colours,
/// yellow sun, blue rain, rather than us hardcoding a palette. It fails a contrast check, though,
/// and a screenshot is what exposed it. Several conditions have no coloured element at all:
/// `cloud.fill`, `cloud.moon.fill` and `moon.stars.fill` render **white**, which is invisible on a
/// light background. In the light-mode day-detail screenshot the 1 am and 4 am rows appeared to
/// have no icon whatsoever.
///
/// `.hierarchical` with the semantic accent colour gives every one of the eight conditions a
/// legible silhouette in both appearances, at the cost of the sun no longer being yellow. Contrast
/// is not negotiable and colour charm is, WCAG AA.
struct ConditionBadge: View {
    let condition: WeatherCondition
    let isDaytime: Bool
    var size: CGFloat = 64

    var body: some View {
        ScaledSymbol(condition.symbolName(isDaytime: isDaytime), baseSize: size)
            .symbolRenderingMode(.hierarchical)
            .foregroundStyle(Color.skyAccent)
            // Glass gives the badge depth without competing with the symbol for attention.
            .skyGlass(.badge)
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
