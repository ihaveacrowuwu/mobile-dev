import SwiftUI

/// The 4 pt spacing scale.
///
/// Every padding, gap and inset comes from here. Never write a raw `.padding(12)` in a view.
enum Spacing {
    /// 2 pt: hairline separation, e.g. between a label and its value.
    static let xxs: CGFloat = 2
    /// 4 pt: tight grouping inside a single component.
    static let xs: CGFloat = 4
    /// 8 pt: related items within a card.
    static let sm: CGFloat = 8
    /// 16 pt: the default. Screen edge insets and gaps between cards.
    static let md: CGFloat = 16
    /// 24 pt: separation between distinct sections.
    static let lg: CGFloat = 24
    /// 32 pt: major visual breaks.
    static let xl: CGFloat = 32
    /// 48 pt: around empty-state and error illustrations.
    static let xxl: CGFloat = 48
}

/// Corner radii, kept consistent so cards and sheets share a visual language.
enum Radius {
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
}

/// Minimum interactive sizes.
///
/// 44 pt is Apple's Human Interface Guidelines minimum for a touch target. Anything tappable must
/// be at least this big even when the visible art is smaller.
enum TouchTarget {
    static let minimum: CGFloat = 44
}
