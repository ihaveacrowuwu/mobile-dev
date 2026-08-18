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
                        onSelectPage: onSelectPage,
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
            if state.showsPageIndicator {
                ToolbarItem(placement: .topBarTrailing) {
                    LocationMenu(state: state, onSelectPage: onSelectPage)
                }
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
    let onSelectPage: (Int) -> Void
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
                        PlaceHeading(location: page.location)

                        CurrentConditionsHero(
                            weather: weather,
                            unit: state.preferences.temperatureUnit,
                            // The switcher above already names the place.
                            showsLocationName: false,
                            // Tapping the hero pushes the full detail screen, the push half of
                            // the navigation hierarchy, reachable from Home.
                            onTap: { onOpenDetail(weather.locationID) }
                        )

                        // Between the reading and the strip, centred: the indicator belongs to the
                        // pager, so it sits directly under what paging changes rather than off in a
                        // corner of the chrome.
                        if state.showsPageIndicator, let current = state.location {
                            PageScrubber(
                                count: state.pages.count,
                                selection: Binding(get: { state.selectedIndex }, set: onSelectPage),
                                announcement: "Showing \(current.name), "
                                    + "\(state.selectedIndex + 1) of \(state.pages.count)"
                            )
                            .frame(height: Self.scrubberHeight)
                        }

                        HourlyStrip(
                            // Every page draws its own strip, including the ones off-screen. The
                            // slice is cheap, and skipping it made the section appear mid-swipe.
                            hours: state.hourlyWindow(for: page),
                            timeZone: weather.timeZone,
                            unit: state.preferences.temperatureUnit
                        )

                        if let sun = weather.sunPath() {
                            SunPathCard(
                                progress: sun.progress,
                                sunriseLabel: sun.sunriseLabel,
                                sunsetLabel: sun.sunsetLabel,
                                daylightLabel: sun.daylightLabel,
                                announcement: sun.announcement
                            )
                        }

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

    /// `UIPageControl` sizes itself; this only reserves the row.
    private static let scrubberHeight: CGFloat = 28
}

/// A menu of the saved places, for jumping straight to one.
///
/// Icon-only, and only present when there is more than one place. The name used to live in this
/// button, which made the most important word on the screen the smallest: it is now the page's
/// heading, so all this has to do is offer the list. Shown as a `Picker` so the place on screen
/// carries a tick, which a column of identical buttons cannot.
private struct LocationMenu: View {
    let state: HomeUiState
    let onSelectPage: (Int) -> Void

    var body: some View {
        Menu {
            Picker("Place", selection: Binding(get: { state.selectedIndex }, set: onSelectPage)) {
                ForEach(Array(state.pages.enumerated()), id: \.element.id) { index, page in
                    Text(page.location.displayName).tag(index)
                }
            }
        } label: {
            Image(systemName: "list.bullet")
        }
        .accessibilityLabel("Choose a place, showing \(state.location?.name ?? "")")
    }
}

/// The place, as the page's heading.
///
/// The region line beneath it disambiguates saved places that share a name.
private struct PlaceHeading: View {
    let location: SavedLocation

    private var region: String? {
        let parts = location.displayName
            .split(separator: ",")
            .dropFirst()
            .map { $0.trimmingCharacters(in: .whitespaces) }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    var body: some View {
        VStack(spacing: Spacing.xxs) {
            Text(location.name)
                .font(.largeTitle.weight(.semibold))
                .minimumScaleFactor(0.6)
                .lineLimit(2)
                .multilineTextAlignment(.center)
            if let region {
                Text(region)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        // One announcement: a place and where it is, not two fragments.
        .accessibilityElement(children: .combine)
    }
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
