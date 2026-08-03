import SwiftUI

// The four screen states, implemented once and reused everywhere, with accessibility wired in.
//
// The Android counterpart is `core/designsystem/component/StateViews.kt`.
//
// The full-screen states use **no glass**: glass is for floating layers over content, and a
// full-screen state *is* the content. Only [StaleDataBanner], which floats over cached content, is
// glass. Buttons use `.glass` / `.glassProminent`, the iOS 26 replacement for `.bordered` /
// `.borderedProminent`.

/// Full-screen loader. Shown only when there is genuinely nothing cached to render.
struct LoadingView: View {
    var message: String?

    var body: some View {
        VStack(spacing: Spacing.md) {
            ProgressView()
                .controlSize(.large)
            if let message {
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(Spacing.lg)
        // One announcement for the whole state, rather than VoiceOver reading a
        // decorative spinner.
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message ?? "Loading")
    }
}

/// Full-screen error. Used only when no cached data exists, otherwise show
/// ``StaleDataBanner`` over the content instead.
struct ErrorView: View {
    let error: AppError
    /// `nil` hides the retry button, for errors where retrying cannot help.
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: error.symbolName)
                .font(.system(size: 56))
                .foregroundStyle(Color.skyError)
                // Decorative: the title and message already carry the meaning.
                .accessibilityHidden(true)

            Text(error.title)
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)

            Text(error.message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            // Only offered when retrying can actually succeed, see AppError.isRetryable.
            if let onRetry, error.isRetryable {
                Button("Retry", action: onRetry)
                    .buttonStyle(.glass)
                    .padding(.top, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(Spacing.lg)
    }
}

/// Empty state, a valid, non-error condition such as "no saved locations yet".
struct EmptyStateView: View {
    let title: String
    let message: String
    var systemImage: String = "tray"
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 56))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)

            Text(title)
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    // Prominent glass: this is the screen's single primary action.
                    .buttonStyle(.glassProminent)
                    .padding(.top, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(Spacing.lg)
    }
}

/// Non-blocking banner shown **above cached content** when a refresh failed or the data
/// is stale.
///
/// This is the visible half of the offline-first promise: the user keeps their data and is
/// merely told it might be out of date. It is the one surface in the app that genuinely
/// floats over content, so it is the one that gets tinted glass.
struct StaleDataBanner: View {
    let message: String
    var onRetry: (() -> Void)?
    var onDismiss: (() -> Void)?

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "clock.arrow.circlepath")
                .accessibilityHidden(true)

            Text(message)
                .font(.footnote)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let onRetry {
                Button("Retry", action: onRetry)
                    .font(.footnote.weight(.semibold))
                    .buttonStyle(.glass)
            }
            if let onDismiss {
                Button {
                    onDismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.footnote)
                }
                .buttonStyle(.glass)
                .accessibilityLabel("Dismiss")
            }
        }
        .frame(maxWidth: .infinity)
        .skyGlass(.notice)
        // Announced as soon as it appears, so a VoiceOver user learns the data is stale
        // without having to go looking for the banner.
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Previews

#Preview("Loading") {
    LoadingView(message: "Fetching the latest weather…")
}

#Preview("Error: offline") {
    ErrorView(error: .offline, onRetry: {})
}

#Preview("Error: no API key") {
    // No Retry button: retrying a missing key can never succeed.
    ErrorView(error: .unauthorized, onRetry: {})
}

#Preview("Empty") {
    EmptyStateView(
        title: "No locations yet",
        message: "Add a place and SkyCast will keep its forecast ready, even offline.",
        systemImage: "mappin.and.ellipse",
        actionTitle: "Add location",
        action: {}
    )
}

#Preview("Stale banner over content") {
    // Previewed over a gradient, because glass samples what is behind it and is invisible against
    // a flat colour.
    ZStack {
        LinearGradient(
            colors: [.blue, .purple, .orange],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()

        SkyGlassGroup {
            StaleDataBanner(
                message: "Offline, showing data from 20 minutes ago",
                onRetry: {},
                onDismiss: {}
            )
            .padding(Spacing.md)
        }
    }
}
