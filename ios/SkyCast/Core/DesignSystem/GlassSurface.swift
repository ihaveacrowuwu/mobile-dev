import SwiftUI

/// SkyCast's **Liquid Glass** vocabulary.
///
/// Every use of the iOS 26 glass APIs in the app goes through this file, so there is one place to
/// tune the treatment and one place to change if a signature shifts.
///
/// Liquid Glass is for **floating, functional layers**: controls, bars, and cards that sit above
/// content. It is not for large content backgrounds, because glass on glass, or glass behind
/// long-form text, destroys legibility.
///
/// - ✅ the stale-data banner, action buttons
/// - ❌ the page background, list row fills, the main reading, or anything already inside a glass
///   container
///
/// Standard SwiftUI components (`TabView`, toolbars, sheets, `Form`) adopt Liquid Glass
/// **automatically** when built against the iOS 26 SDK. This file exists only for custom surfaces.
enum GlassRole {
    /// A transient notice layered over content, e.g. the stale-data banner. Tinted so it
    /// carries meaning as well as depth.
    case notice
    /// A small decorative container.
    case badge
    /// A floating control that sits over scrolling content, like the page indicator on Home.
    ///
    /// This is the role glass is actually *for*, a functional layer above content, not a content
    /// surface. It is the same treatment Apple's Weather app gives the page control at the bottom of its
    /// screen, and the reason to give the dots a ground of their own is contrast: bare dots on a heavily
    /// washed background lose definition exactly when the weather behind them is most colourful.
    case control
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
        case .badge, .control: return nil
        case .notice: return .skyWarning
        }
    }

    private var shape: AnyShape {
        switch role {
        case .notice: AnyShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        case .badge: AnyShape(Circle())
        case .control: AnyShape(Capsule())
        }
    }

    private var padding: EdgeInsets {
        switch role {
        case .notice:
            EdgeInsets(top: Spacing.sm, leading: Spacing.md, bottom: Spacing.sm, trailing: Spacing.sm)
        case .badge:
            EdgeInsets(top: Spacing.md, leading: Spacing.md, bottom: Spacing.md, trailing: Spacing.md)
        case .control:
            // **Zero**, and the caller sizes the control instead. Padding here put an inert margin around
            // the control it wrapped: the capsule looked draggable across its whole width, but only the
            // dots themselves were inside `UIPageControl`, so a drag that began on the glass did nothing.
            // A control has to own every pixel that looks like part of it.
            EdgeInsets()
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
