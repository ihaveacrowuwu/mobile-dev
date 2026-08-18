import SwiftUI

/// The app's card surface: a **frosted, translucent** panel that the weather shows through.
///
/// ## The material
///
/// This is deliberately not Liquid Glass. Liquid Glass is reserved for floating,
/// functional layers, bars, toolbars, controls sitting *above* content, and these cards are content.
/// SwiftUI's **materials** are the sanctioned way to make a content surface translucent: they blur what
/// is behind them, take their brightness from the appearance, and become opaque automatically when the
/// reader turns on **Reduce Transparency**. Hand-rolled alpha does none of those things, and glass on a
/// scrolling content card would break the one rule this project breaks most often.
///
/// ## Why there is no tint any more
///
/// The material samples what is behind it. An opaque surface with the location's
/// container colour mixed in; the second kept that colour at 22% over a material. Both *painted a hue on
/// the card*, and a chosen hue can only ever approximate the background, a warm card over a cool patch of
/// gradient reads as a mismatch, because it is one.
///
/// The material already samples what is actually behind it. Adding a colour on top does not help it match;
/// it fights what the sampling got right. So the tint is gone and the material is thinner, and the card
/// harmonises with the background because it *is* the background, blurred. Swiping between a clear place
/// and an overcast one still shifts every card, but now it shifts by the amount the background shifted
/// rather than by a palette entry's idea of it.
///
/// Defined once here because the same treatment had been written out six times: the detail grid, the sun
/// path card, two Moon cards and two METAR cards had each grown their own copy.
extension View {
    /// Frosts this view's background as a card.
    ///
    /// - Parameter cornerRadius: defaults to ``Radius/md``; pass ``Radius/lg`` for a hero-sized panel.
    func frostedCard(cornerRadius: CGFloat = Radius.md) -> some View {
        modifier(FrostedCardModifier(cornerRadius: cornerRadius))
    }
}

private struct FrostedCardModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content.background {
            let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            shape
                // `.ultraThin` rather than `.thin`: the thinner material lets more of the weather through,
                // which is what the surface is for. Legibility is not at risk, materials carry
                // vibrancy for the text drawn on them, and they turn opaque by themselves under Reduce
                // Transparency, which is the case this would otherwise fail.
                .fill(.ultraThinMaterial)
                // A rim that catches the light at the top and fades away by the bottom. It is what makes
                // the panel read as a pane with an edge rather than as a lighter rectangle, and it works
                // in both appearances because it adds light rather than assuming a dark ground.
                .overlay {
                    shape.strokeBorder(
                        .linearGradient(
                            colors: [.white.opacity(Self.rimOpacity), .white.opacity(0)],
                            startPoint: .top,
                            endPoint: .bottom
                        ),
                        lineWidth: 0.75
                    )
                }
        }
    }

    private static let rimOpacity: Double = 0.35
}
