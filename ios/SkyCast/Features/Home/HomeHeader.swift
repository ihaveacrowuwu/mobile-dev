import SwiftUI

/// The place name and the page dots, pinned above the pager.
///
/// One line for the name and one row of dots. The region reads as glass, so the page passes
/// underneath rather than stopping at a bar.
struct HomeStickyHeader: View {
    let state: HomeUiState
    let onSelectPage: (Int) -> Void

    var body: some View {
        VStack(spacing: Spacing.xxs) {
            // The name and the place-list button share one row, which is the only way to guarantee they
            // share a baseline. The button was a `ToolbarItem`, a floating glass control for free, but a
            // toolbar item lives in the navigation bar, a row above anything this header can put beside it.
            // `.buttonStyle(.glass)` gets the same material here, under this view's control.
            ZStack {
                if let location = state.location {
                    Text(location.name)
                        .font(.title.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .padding(.horizontal, Self.menuClearance)
                        .accessibilityAddTraits(.isHeader)
                }

                if state.showsPageIndicator {
                    HStack {
                        Spacer()
                        LocationMenu(state: state, onSelectPage: onSelectPage)
                            .buttonStyle(GlassButtonStyle())
                    }
                }
            }
            .frame(height: Self.toolbarRowHeight)
            .padding(.horizontal, Spacing.md)

            if state.showsPageIndicator, let current = state.location {
                PageScrubber(
                    count: state.pages.count,
                    selection: Binding(get: { state.selectedIndex }, set: onSelectPage),
                    announcement: "Showing \(current.name), \(state.selectedIndex + 1) of \(state.pages.count)"
                )
                // Sized to its content rather than stretched, so the capsule hugs the dots.
                .frame(width: scrubberWidth, height: Self.scrubberHeight)
                .skyGlass(.control)
            }
        }
        .padding(.top, Spacing.xs)
        // Tight at the bottom: this is what sets the gap between the pill and the weather icon below it,
        // and the page adds its own padding underneath.
        .padding(.bottom, Spacing.xxs)
        .frame(maxWidth: .infinity)
        // No background of its own. The page's weather already runs behind this, and a bar would put an
        // opaque strip across the top of the one screen built to have content pass under its chrome.
    }

    /// A navigation-bar row's height, which is what the name and the button share.
    private static let toolbarRowHeight: CGFloat = 44

    /// Keeps a long centred name from running under the button beside it.
    private static let menuClearance: CGFloat = 52

    /// `UIPageControl` reports an intrinsic width, but inside a capsule it stretched to fill the row.
    /// Measured from the control's own metrics: roughly 18 points per dot.
    private var scrubberWidth: CGFloat {
        CGFloat(state.pages.count) * Self.scrubberDotSpacing
    }

    private static let scrubberDotSpacing: CGFloat = 18
    private static let scrubberHeight: CGFloat = 28
}
