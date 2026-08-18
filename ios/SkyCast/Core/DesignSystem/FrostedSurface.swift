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
/// ## Why it replaced a solid fill
///
/// An opaque `skySurface` with the location's container colour mixed in would make
/// them *coloured* rather than *translucent*, so a card sat on the weather background like a sticker
/// rather than a pane in front of it. The tint survives at a much lower opacity, enough that swiping
/// between a clear place and an overcast one still shifts the cards, which is the part worth keeping.
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

    @Environment(\.weatherSurfaceTint) private var weatherSurfaceTint

    func body(content: Content) -> some View {
        content.background {
            let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            shape
                .fill(.thinMaterial)
                .overlay {
                    if let weatherSurfaceTint {
                        shape.fill(weatherSurfaceTint.opacity(Self.tintOpacity))
                    }
                }
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

    /// Low, because the material is now doing the work the colour used to do. High enough that the
    /// difference between a clear page and an overcast one is still visible on the cards.
    private static let tintOpacity: Double = 0.22
    private static let rimOpacity: Double = 0.35
}
