import SwiftUI

/// The Today tab.
///
/// Two views, deliberately:
///
/// - ``TodayScreen`` is the **stateful** entry point. It builds the view model from the
///   environment container and does nothing else.
/// - ``TodayContent`` is **stateless**, state in, closures out. That is what makes it previewable
///   in every state and assertable without a live network or database.
///
/// Follow this split for every screen. It is the exact counterpart of the `TodayScreen` /
/// `TodayContent` pair on Android.
struct TodayScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: TodayViewModel?
    /// The pushed detail destination. `nil` means nothing is pushed; driving the stack from a value
    /// rather than a `NavigationLink` inside the hero keeps the hero reusable on the detail screen
    /// itself, where there is nowhere further to push.
    @State private var detailLocationID: Int64?

    var body: some View {
        Group {
            if let viewModel {
                TodayContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() },
                    onDismissBanner: viewModel.dismissBanner,
                    onSelectPage: viewModel.selectPage,
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
                viewModel?.start()
            } else {
                // A place may have been added or removed on the Locations tab while this screen
                // was off-screen.
                await viewModel?.reload()
            }
        }
        .onDisappear { viewModel?.stop() }
    }
}

/// The stateless half.
struct TodayContent: View {
    let state: TodayUiState
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void
    let onSelectPage: (Int) -> Void
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
                systemImage: "mappin.and.ellipse"
            )
        } else if state.showsFullScreenError, let error = state.error {
            ErrorView(error: error, onRetry: { Task { await onRefresh() } })
        } else {
            pager
        }
    }

    /// The saved places, one per page.
    ///
    /// `TabView` in page style rather than a hand-rolled `ScrollView` with paging: it supplies the
    /// gesture, the rubber-banding, the index binding and, importantly, VoiceOver's swipe
    /// navigation between pages, none of which a custom implementation would get right for free.
    ///
    /// The menu above it stays, because a gesture with no visible affordance is undiscoverable:
    /// someone who never swipes would not learn there is more than one page.
    private var pager: some View {
        VStack(spacing: 0) {
            LocationSwitcher(state: state, onSelectPage: onSelectPage)
                .padding(.horizontal, Spacing.md)
                .padding(.bottom, Spacing.sm)

            TabView(selection: selectionBinding) {
                ForEach(Array(state.pages.enumerated()), id: \.element.id) { index, page in
                    TodayPageView(
                        page: page,
                        state: state,
                        isSelected: index == state.selectedIndex,
                        onRefresh: onRefresh,
                        onDismissBanner: onDismissBanner,
                        onOpenDetail: onOpenDetail
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
        .background(Color.skyBackground)
    }

    private var selectionBinding: Binding<Int> {
        Binding(get: { state.selectedIndex }, set: onSelectPage)
    }
}

/// One page: the hero reading, the hourly strip and the detail tiles for a single place.
private struct TodayPageView: View {
    let page: TodayPage
    let state: TodayUiState
    let isSelected: Bool
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void
    let onOpenDetail: (Int64) -> Void

    var body: some View {
        ScrollView {
            // One glass container for every floating surface on this page, so the banner and hero
            // behave as a single material.
            SkyGlassGroup(spacing: Spacing.md) {
                VStack(spacing: Spacing.md) {
                    // The banner belongs to the page on screen: a stale reading for Malé should
                    // not warn the user while they are looking at London.
                    if isSelected, state.showsStaleBanner {
                        StaleDataBanner(
                            message: state.staleBannerMessage,
                            onRetry: { Task { await onRefresh() } },
                            onDismiss: onDismissBanner
                        )
                    }

                    if let weather = page.weather.data {
                        CurrentConditionsHero(
                            weather: weather,
                            unit: state.preferences.temperatureUnit,
                            // The switcher above already names the place.
                            showsLocationName: false,
                            // Tapping the hero pushes the full detail screen, the push half of
                            // the navigation hierarchy, reachable from Today.
                            onTap: { onOpenDetail(weather.locationID) }
                        )

                        HourlyStrip(
                            // Only the visible page computes its window: it reads the clock, and
                            // running it for every page on every render is work nobody sees.
                            hours: isSelected ? state.hourlyWindow() : [],
                            timeZone: weather.timeZone,
                            unit: state.preferences.temperatureUnit
                        )

                        WeatherDetailGrid(details: weather.details(preferences: state.preferences))
                    } else {
                        // A page reached by swiping ahead of its data.
                        LoadingView(message: "Fetching the latest weather…")
                    }
                }
                .padding(.vertical, Spacing.md)
                .padding(.horizontal, Spacing.md)
            }
        }
        // Softens where content meets the navigation bar and the minimised tab bar.
        .scrollEdgeEffectStyle(.soft, for: .all)
        // Native pull-to-refresh; also drives the Retry action above.
        .refreshable { await onRefresh() }
    }
}

/// The place currently shown, with a menu of the others and a page indicator.
///
/// The dots report position and are not tap targets, 8-point dots would fail the 44-point minimum
/// for no gain, and the menu beside them is the control.
private struct LocationSwitcher: View {
    let state: TodayUiState
    let onSelectPage: (Int) -> Void

    var body: some View {
        if let current = state.location {
            HStack {
                Menu {
                    ForEach(Array(state.pages.enumerated()), id: \.element.id) { index, page in
                        Button(page.location.displayName) { onSelectPage(index) }
                    }
                } label: {
                    // An HStack rather than a `Label`, which puts its icon first: a disclosure
                    // chevron belongs after the thing it discloses.
                    HStack(spacing: Spacing.xs) {
                        Text(current.name)
                            .font(.headline)
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.semibold))
                    }
                }
                .accessibilityLabel("Choose a place, showing \(current.name)")

                Spacer()

                if state.showsPageIndicator {
                    PageDots(count: state.pages.count, selectedIndex: state.selectedIndex, currentName: current.name)
                }
            }
        }
    }
}

private struct PageDots: View {
    let count: Int
    let selectedIndex: Int
    let currentName: String

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(0..<count, id: \.self) { index in
                Circle()
                    .fill(index == selectedIndex ? Color.skyAccent : Color.secondary.opacity(0.3))
                    .frame(width: index == selectedIndex ? 8 : 6, height: index == selectedIndex ? 8 : 6)
            }
        }
        // One announcement for the whole indicator; individual dots mean nothing aloud.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Showing \(currentName), \(selectedIndex + 1) of \(count)")
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
            onSelectPage: { _ in },
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
            onSelectPage: { _ in },
            onOpenDetail: { _ in }
        )
    }
}

#Preview("Offline, no cache") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(refreshError: .offline),
            onRefresh: {},
            onDismissBanner: {},
            onSelectPage: { _ in },
            onOpenDetail: { _ in }
        )
    }
}
