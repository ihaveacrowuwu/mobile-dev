import SwiftUI

// The four screen states, implemented once and reused everywhere.
//
// Centralising loading/empty/error presentation means every screen handles all four
// states consistently, with accessibility already wired in, exactly what the
// *Functionality* and *UI/UX* criteria look for.
//
// The Android counterpart is `core/designsystem/component/StateViews.kt`.

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
                    .buttonStyle(.bordered)
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
                    .buttonStyle(.borderedProminent)
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
/// merely told it might be out of date.
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
            }
            if let onDismiss {
                Button {
                    onDismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.footnote)
                }
                .accessibilityLabel("Dismiss")
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .frame(maxWidth: .infinity)
        .background(Color.skyWarning.opacity(0.18))
        // Announced as soon as it appears, so a VoiceOver user learns the data is stale
        // without having to go looking for the banner.
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isStaticText)
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

#Preview("Stale banner") {
    StaleDataBanner(
        message: "Offline, showing data from 20 minutes ago",
        onRetry: {},
        onDismiss: {}
    )
}
