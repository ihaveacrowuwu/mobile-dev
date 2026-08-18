import SwiftUI

/// SkyCast's **Liquid Glass** vocabulary.
///
/// Every use of the iOS 26 glass APIs in the app goes through this file. Two reasons:
///
/// 1. **One place to tune.** Glass has a small number of knobs (regular vs clear, tint,
///    interactivity, shape) and scattering ad-hoc `.glassEffect(...)` calls across screens
///    is how an app ends up with six subtly different glass treatments.
/// 2. **One place to fix.** These are new APIs; if a signature shifts, the change lands
///    here rather than in every view.
///
/// ## When to use glass, and when not to
///
/// Liquid Glass is for **floating, functional layers**, controls, bars, cards that sit
/// above content. It is explicitly *not* for large content backgrounds: Apple's guidance is
/// that glass on glass, or glass behind long-form text, destroys legibility. So:
///
/// - ✅ the stale-data banner, action buttons
/// - ❌ the page background, list row fills, the main reading, or anything already inside a glass
///   container
///
/// The Today hero belongs on the second list. It is the page's
/// content rather than a layer floating over it, and once the toolbar became a set of floating glass
/// controls the hero sat directly under them as the page scrolled, glass over glass, the rule this
/// file exists to keep.
///
/// Standard SwiftUI components (`TabView`, toolbars, sheets, `Form`) adopt Liquid Glass
/// **automatically** when built against the iOS 26 SDK. Nothing in this file is needed for
/// those, it exists only for our custom surfaces.
enum GlassRole {
    /// A transient notice layered over content, e.g. the stale-data banner. Tinted so it
    /// carries meaning as well as depth.
    case notice
    /// A small decorative container.
    case badge
}

extension View {
    /// Applies the app's glass treatment for a given [GlassRole].
    ///
    /// - Parameters:
    ///   - role: which of the app's three glass treatments to use.
    ///   - tint: overrides the role's default tint. A tint on glass is a semantic signal such as
    ///     warning or error.
    func skyGlass(_ role: GlassRole, tint: Color? = nil) -> some View {
        modifier(SkyGlassModifier(role: role, tint: tint))
    }
}

private struct SkyGlassModifier: ViewModifier {
    let role: GlassRole
    let tint: Color?

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .glassEffect(glass, in: shape)
    }

    /// `.regular` everywhere rather than `.clear`.
    ///
    /// `.clear` is thinner and more transparent, striking over photography, but it cannot
    /// guarantee contrast for the text we put on it. Since this app's glass always carries
    /// readable content, `.regular` is the correct default.
    private var glass: Glass {
        let base = Glass.regular
        guard let resolvedTint else { return base }
        return base.tint(resolvedTint)
    }

    private var resolvedTint: Color? {
        if let tint {
            return tint
        }
        switch role {
        case .badge: return nil
        case .notice: return .skyWarning
        }
    }

    private var shape: AnyShape {
        switch role {
        case .notice: AnyShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        case .badge: AnyShape(Circle())
        }
    }

    private var padding: EdgeInsets {
        switch role {
        case .notice:
            EdgeInsets(top: Spacing.sm, leading: Spacing.md, bottom: Spacing.sm, trailing: Spacing.sm)
        case .badge:
            EdgeInsets(top: Spacing.md, leading: Spacing.md, bottom: Spacing.md, trailing: Spacing.md)
        }
    }
}

/// Groups sibling glass surfaces so they blend and morph as one material.
///
/// Without a container, two adjacent glass views each sample the background independently and the
/// seam between them is visible. `GlassEffectContainer` makes them behave as one piece of glass.
///
/// Use this whenever two or more `skyGlass` views sit near each other.
struct SkyGlassGroup<Content: View>: View {
    var spacing: CGFloat = Spacing.md
    @ViewBuilder var content: () -> Content

    var body: some View {
        GlassEffectContainer(spacing: spacing) {
            content()
        }
    }
}
