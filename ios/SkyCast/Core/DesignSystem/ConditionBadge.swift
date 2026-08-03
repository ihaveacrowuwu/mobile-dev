import SwiftUI

/// The current condition, as an SF Symbol on a circular piece of Liquid Glass.
///
/// The direct counterpart to Android's `WeatherConditionBadge`, which uses Material 3
/// Expressive's `MaterialShapes`. The two platforms deliberately diverge here, and that
/// divergence is the point: Expressive expresses meaning through **shape**, Liquid Glass
/// through **depth and material**. Forcing one platform to imitate the other would produce
/// something that looks wrong on both.
///
/// The symbol itself uses `.symbolRenderingMode(.multicolor)` so SF Symbols supplies the
/// semantically correct colours, yellow sun, blue rain, rather than us hardcoding a
/// palette that then fails in dark mode.
struct ConditionBadge: View {
    let condition: WeatherCondition
    let isDaytime: Bool
    var size: CGFloat = 64

    var body: some View {
        ScaledSymbol(condition.symbolName(isDaytime: isDaytime), baseSize: size)
            .symbolRenderingMode(.multicolor)
            // Glass gives the badge depth without a coloured fill, so the symbol's own
            // colours stay accurate.
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
