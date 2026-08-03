import SwiftUI

/// Scaffolding for a screen whose navigation and state plumbing are finished but whose
/// content is not yet built.
///
/// Every placeholder is a **real, reachable destination** with working back navigation and,
/// where relevant, a link that exercises the onward route. That is deliberate: the
/// navigation hierarchy can be demonstrated, screenshotted and UI-tested before any of the
/// feature content exists.
///
/// Delete each usage as its screen is implemented. None should survive into the final
/// submission.
struct PlaceholderScreen<Destination: View>: View {
    let title: String
    let plannedContent: String
    var linkTitle: String?
    @ViewBuilder var destination: () -> Destination

    var body: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "hammer")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)

            Text(plannedContent)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if let linkTitle {
                NavigationLink(linkTitle, destination: destination)
                    .buttonStyle(.glass)
                    .padding(.top, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(Spacing.lg)
        .background(Color.skyBackground)
        .navigationTitle(title)
    }
}

extension PlaceholderScreen where Destination == EmptyView {
    /// Convenience for a leaf placeholder with no onward navigation.
    init(title: String, plannedContent: String) {
        self.init(
            title: title,
            plannedContent: plannedContent,
            linkTitle: nil,
            destination: { EmptyView() }
        )
    }
}
