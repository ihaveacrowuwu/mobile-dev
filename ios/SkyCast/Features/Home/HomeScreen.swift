import SwiftUI

/// The Home tab.
///
/// - ``HomeScreen`` is the **stateful** entry point. It builds the view model from the
///   environment container and does nothing else.
/// - ``HomeContent`` is **stateless**: state in, closures out.
struct HomeScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: HomeViewModel?
    /// The pushed detail destination. `nil` means nothing is pushed; driving the stack from a value
    /// rather than a `NavigationLink` inside the hero keeps the hero reusable on the detail screen
    /// itself, where there is nowhere further to push.
    @State private var detailLocationID: Int64?

    var body: some View {
        Group {
            if let viewModel {
                HomeContent(
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
        .toolbarTitleDisplayMode(.inline)
        .navigationDestination(item: $detailLocationID) { locationID in
            LocationDetailScreen(locationID: locationID)
        }
        .task {
            // @Environment is not available during init, so the view model is built here.
            if viewModel == nil {
                viewModel = HomeViewModel(
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
struct HomeContent: View {
    let state: HomeUiState
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
    /// `TabView` in page style supplies the gesture, the rubber-banding, the index binding and
    /// VoiceOver's swipe navigation between pages. The place name lives in the toolbar, and the
    /// menu beside it offers the list for readers who do not swipe.
    private var pager: some View {
        // The GeometryReader recovers the safe-area insets after they are ignored. A paged
        // TabView lays its pages out inside the safe area, so the pager ignores the insets and each
        // page puts them back as content padding.
        GeometryReader { proxy in
            TabView(selection: selectionBinding) {
                ForEach(Array(state.pages.enumerated()), id: \.element.id) { index, page in
                    HomePageView(
                        page: page,
                        state: state,
                        isSelected: index == state.selectedIndex,
                        onRefresh: onRefresh,
                        onDismissBanner: onDismissBanner,
                        onOpenDetail: onOpenDetail,
                        chromeInsets: proxy.safeAreaInsets
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            // On the TabView, not on the GeometryReader: a reader that ignores the safe area
            // reports no insets, which would leave the pages with no padding.
            .ignoresSafeArea(.container, edges: .vertical)
        }
        // Without this the navigation bar paints its own background across the top of the screen.
        // Hiding it leaves only the toolbar items.
        .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                LocationMenu(state: state, onSelectPage: onSelectPage)
            }
            if state.showsPageIndicator, let current = state.location {
                ToolbarItem(placement: .topBarTrailing) {
                    PageIndicator(
                        count: state.pages.count,
                        selectedIndex: state.selectedIndex,
                        currentName: current.name
                    )
                }
                // Opts the indicator out of the toolbar's shared glass. Left in, the system gave it
                // the same floating capsule as the menu beside it, which reads as a button, and it
                // is not one: it reports position and cannot be pressed.
                .sharedBackgroundVisibility(.hidden)
            }
        }
        // Follows the place on screen, so swiping from a clear Malé to an overcast London changes
        // the weather of the whole screen, not just the numbers on it.
        .weatherBackground(
            condition: state.weather?.condition ?? .unknown,
            isDaytime: state.weather?.isDaytime ?? true
        )
    }

    private var selectionBinding: Binding<Int> {
        Binding(get: { state.selectedIndex }, set: onSelectPage)
    }
}

/// One page: the hero reading, the hourly strip and the detail tiles for a single place.
private struct HomePageView: View {
    let page: HomePage
    let state: HomeUiState
    let isSelected: Bool
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void
    let onOpenDetail: (Int64) -> Void
    /// The insets the pager gave up so content could run behind the bars; see
    /// ``HomeContent/pager``. Re-applied here as content padding.
    let chromeInsets: EdgeInsets

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
                            // the navigation hierarchy, reachable from Home.
                            onTap: { onOpenDetail(weather.locationID) }
                        )

                        HourlyStrip(
                            // Every page draws its own strip, including the ones off-screen. The
                            // slice is cheap, and skipping it made the section appear mid-swipe.
                            hours: state.hourlyWindow(for: page),
                            timeZone: weather.timeZone,
                            unit: state.preferences.temperatureUnit
                        )

                        WeatherDetailGrid(details: weather.details(preferences: state.preferences))
                    } else {
                        // A page reached by swiping ahead of its data.
                        LoadingView(message: "Fetching the latest weather…")
                    }
                }
                .padding(.horizontal, Spacing.md)
                // The inset is the gap: adding a further Spacing.md on top of a status bar plus a
                // toolbar left the hero sitting noticeably low on the page.
                .padding(.top, chromeInsets.top)
                // The tab bar's own inset, plus room to scroll the last tile clear of it.
                .padding(.bottom, chromeInsets.bottom + Self.tabBarClearance)
            }
        }
        // Softens where content meets the navigation bar and the minimised tab bar.
        .scrollEdgeEffectStyle(.soft, for: .all)
        // Native pull-to-refresh; also drives the Retry action above.
        .refreshable { await onRefresh() }
    }

    /// Enough to scroll the last tile out from under the minimised tab bar.
    private static let tabBarClearance: CGFloat = 56
}

/// The place currently shown, and a menu of the others.
///
/// Lives in the toolbar, so it is a floating glass control rather than a strip of the page, see
/// ``HomeContent/pager``.
private struct LocationMenu: View {
    let state: HomeUiState
    let onSelectPage: (Int) -> Void

    var body: some View {
        if let current = state.location {
            Menu {
                // A picker rather than plain buttons: the menu then shows a tick beside the place
                // on screen, which a list of identical-looking buttons cannot.
                Picker("Place", selection: Binding(get: { state.selectedIndex }, set: onSelectPage)) {
                    ForEach(Array(state.pages.enumerated()), id: \.element.id) { index, page in
                        Text(page.location.displayName).tag(index)
                    }
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
        }
    }
}

/// Which page of how many, over the page rather than in the chrome.
///
/// Dots up to a handful of places, then a count. A row of dots is the familiar carousel affordance
/// and reads instantly at three or four, but it degrades badly: at a dozen saved places it becomes
/// a line of specks nobody can count, and it grows without bound. "7 of 12" costs the same space
/// whatever the number.
///
/// Deliberately not a control. It reports position; the menu in the toolbar is how you move.
/// Eight-point dots would fail the 44-point minimum touch target for no gain.
private struct PageIndicator: View {
    let count: Int
    let selectedIndex: Int
    let currentName: String

    var body: some View {
        Group {
            if count <= Self.dotLimit {
                HStack(spacing: Spacing.xs) {
                    ForEach(0..<count, id: \.self) { index in
                        Circle()
                            .fill(index == selectedIndex ? Color.skyAccent : Color.secondary.opacity(0.35))
                            .frame(width: diameter(for: index), height: diameter(for: index))
                    }
                }
            } else {
                Text("\(selectedIndex + 1) of \(count)")
                    .font(.caption.weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
        }
        // One announcement for the whole indicator; individual dots mean nothing aloud.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Showing \(currentName), \(selectedIndex + 1) of \(count)")
    }

    private func diameter(for index: Int) -> CGFloat {
        index == selectedIndex ? 8 : 6
    }

    /// Six is about where counting dots stops being faster than reading a number.
    private static let dotLimit = 6
}

// MARK: - Previews
// One per state, so every branch is reviewable without a device.

#Preview("Loading") {
    NavigationStack {
        HomeContent(
            state: HomeUiState(isLoading: true),
            onRefresh: {},
            onDismissBanner: {},
            onSelectPage: { _ in },
            onOpenDetail: { _ in }
        )
    }
}

#Preview("Empty") {
    NavigationStack {
        HomeContent(
            state: HomeUiState(hasNoLocation: true),
            onRefresh: {},
            onDismissBanner: {},
            onSelectPage: { _ in },
            onOpenDetail: { _ in }
        )
    }
}

#Preview("Offline, no cache") {
    NavigationStack {
        HomeContent(
            state: HomeUiState(refreshError: .offline),
            onRefresh: {},
            onDismissBanner: {},
            onSelectPage: { _ in },
            onOpenDetail: { _ in }
        )
    }
}
