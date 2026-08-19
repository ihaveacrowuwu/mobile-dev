import SwiftUI

/// The app's card surface: a **frosted, translucent** panel that the weather shows through.
///
/// Built from a SwiftUI material, which blurs what is behind it, takes its brightness from the
/// appearance, and becomes opaque automatically under **Reduce Transparency**.
extension View {
    /// Frosts this view's background as a card.
    ///
    /// - Parameter cornerRadius: defaults to ``Radius/md``; pass ``Radius/lg`` for a hero-sized panel.
    func frostedCard(cornerRadius: CGFloat = Radius.md) -> some View {
        modifier(FrostedCardModifier(cornerRadius: cornerRadius))
    }
}

/// The card surface's tunable values, kept out of the view so they can be tested.
enum FrostedCard {
    /// How much of the material survives.
    static let thinness: Double = 0.4

    /// The material's opacity for the reader's current transparency setting.
    ///
    /// Always 1 under Reduce Transparency, so the surface stays opaque when that setting is on.
    static func materialOpacity(reduceTransparency: Bool) -> Double {
        reduceTransparency ? 1 : thinness
    }
}

private struct FrostedCardModifier: ViewModifier {
    let cornerRadius: CGFloat

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    /// The rim is drawn in the opposite direction in each appearance; see ``rim``.
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.background {
            let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            shape
                .fill(.ultraThinMaterial.opacity(FrostedCard.materialOpacity(reduceTransparency: reduceTransparency)))
                .overlay { shape.strokeBorder(rim, lineWidth: 0.75) }
        }
    }

    /// The card's edge: a lit top edge that fades downward in the dark appearance, and a plain
    /// hairline in the light one.
    ///
    /// The Moon tab forces the dark appearance for its subtree, so its cards take the lit rim in
    /// either app theme.
    private var rim: AnyShapeStyle {
        if colorScheme == .dark {
            AnyShapeStyle(
                .linearGradient(
                    colors: [.white.opacity(Self.litRimOpacity), .white.opacity(0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        } else {
            AnyShapeStyle(Color.black.opacity(Self.hairlineOpacity))
        }
    }

    private static let litRimOpacity: Double = 0.35

    private static let hairlineOpacity: Double = 0.12
}
