import SwiftUI

/// The Today tab.
///
/// Two views, deliberately:
///
/// - ``TodayScreen`` is the **stateful** entry point. It builds the view model from the
///   environment container and does nothing else.
/// - ``TodayContent`` is **stateless**, state in, closures out. That is what makes it
///   previewable in every state and assertable without a live network or database.
///
/// Follow this split for every screen. It is the exact counterpart of the
/// `TodayScreen` / `TodayContent` pair on Android.
struct TodayScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: TodayViewModel?
    /// The pushed detail destination. `nil` means nothing is pushed; driving the stack from a
    /// value rather than a `NavigationLink` inside the hero keeps the hero reusable on the
    /// detail screen itself, where there is nowhere further to push.
    @State private var detailLocationID: Int64?

    var body: some View {
        Group {
            if let viewModel {
                TodayContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() },
                    onDismissBanner: viewModel.dismissBanner,
                    onOpenDetail: { detailLocationID = $0 }
                )
            } else {
                LoadingView()
            }
        }
        .navigationTitle("Today")
        .navigationDestination(item: $detailLocationID) { locationID in
            LocationDetailScreen(locationID: locationID)
        }
        .task {
            // @Environment is not available during init, so the view model is built here.
            if viewModel == nil {
                viewModel = TodayViewModel(
                    weatherRepository: container.weatherRepository,
                    locationRepository: container.locationRepository,
                    settingsStore: container.settingsStore
                )
            }
            viewModel?.start()
        }
        .onDisappear { viewModel?.stop() }
    }
}

/// The stateless half.
struct TodayContent: View {
    let state: TodayUiState
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void
    let onOpenDetail: (Int64) -> Void

    var body: some View {
        // Exactly one branch renders. The ordering encodes the offline-first rules from
        // The offline-first read algorithm: content wins over errors whenever a cache exists.
        if state.showsFullScreenLoader {
            LoadingView(message: "Fetching the latest weather…")
        } else if state.showsEmptyState {
            EmptyStateView(
                title: "No locations yet",
                message: "Add a place and SkyCast will keep its forecast ready, even offline.",
                systemImage: "mappin.and.ellipse",
                actionTitle: nil,
                action: nil
            )
        } else if state.showsFullScreenError, let error = state.error {
            ErrorView(error: error, onRetry: { Task { await onRefresh() } })
        } else {
            content
        }
    }

    private var content: some View {
        ScrollView {
            // One glass container for every floating surface on this screen, so the
            // banner, hero and detail card behave as a single material instead of three
            // independent panes with visible seams between them.
            SkyGlassGroup(spacing: Spacing.md) {
                VStack(spacing: Spacing.md) {
                    if state.showsStaleBanner {
                        StaleDataBanner(
                            message: state.staleBannerMessage,
                            onRetry: { Task { await onRefresh() } },
                            onDismiss: onDismissBanner
                        )
                    }

                    if let weather = state.weather {
                        CurrentConditionsHero(
                            weather: weather,
                            unit: state.preferences.temperatureUnit,
                            // Tapping the hero pushes the full detail screen, the push half of
                            // the navigation hierarchy, reachable from Today.
                            onTap: { onOpenDetail(weather.locationID) }
                        )

                        WeatherDetailGrid(
                            details: weather.details(preferences: state.preferences)
                        )
                    }
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.md)
            }
        }
        // The grouped background is what `Color.skySurface` is designed to sit on. Without it the
        // page is plain white, the detail tiles are white-on-white, and in the light-mode
        // screenshot they had no visible surface at all, the readings floated in space.
        .background(Color.skyBackground)
        // Liquid Glass: softens where content meets the navigation bar and the minimised
        // tab bar, instead of the hard clip that a plain ScrollView would produce.
        .scrollEdgeEffectStyle(.soft, for: .all)
        // Native pull-to-refresh; also drives the Retry action above.
        .refreshable { await onRefresh() }
    }
}

// MARK: - Previews
// One per state, so every branch is reviewable without a device.

#Preview("Loading") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(isLoading: true),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDetail: { _ in }
        )
    }
}

#Preview("Empty") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(hasNoLocation: true),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDetail: { _ in }
        )
    }
}

#Preview("Offline, no cache") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(error: .offline),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDetail: { _ in }
        )
    }
}

#Preview("No API key") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(error: .unauthorized),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDetail: { _ in }
        )
    }
}
