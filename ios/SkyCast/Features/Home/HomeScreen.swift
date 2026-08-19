import SwiftUI

/// The Home tab.
///
/// - ``HomeScreen`` is the **stateful** entry point. It builds the view model from the
///   environment container and does nothing else.
/// - ``HomeContent`` is **stateless**: state in, closures out.
struct HomeScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: HomeViewModel?
    /// The pushed day-detail destination. `nil` means nothing is pushed.
    @State private var selectedDay: DayRoute?

    var body: some View {
        Group {
            if let viewModel {
                HomeContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() },
                    onDismissBanner: viewModel.dismissBanner,
                    onSelectPage: viewModel.selectPage,
                    onSelectDay: { locationID, date in selectedDay = DayRoute(locationID: locationID, date: date) }
                )
            } else {
                LoadingView()
            }
        }
        .toolbarTitleDisplayMode(.inline)
        .navigationDestination(item: $selectedDay) { route in
            DayDetailScreen(locationID: route.locationID, date: route.date)
        }
        .task {
            // @Environment is not available during init, so the view model is built here.
            if viewModel == nil {
                viewModel = HomeViewModel(
                    weatherRepository: container.weatherRepository,
                    locationRepository: container.locationRepository,
                    settingsStore: container.settingsStore,
                    selectedLocationStore: container.selectedLocationStore
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

/// One day of one place, which is what Home has to carry to push the day-detail screen.
///
/// Home pages across several places, so a bare `Date` would be ambiguous: tapping Thursday on Malé
/// must not open Thursday on London.
struct DayRoute: Hashable {
    let locationID: Int64
    let date: Date
}

/// The stateless half.
struct HomeContent: View {
    /// Measured from the sticky header so each page can start below it. A constant would break
    /// when the pill appears or disappears, or when Dynamic Type resizes the name.
    @State private var headerHeight: CGFloat = 0

    let state: HomeUiState
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void
    let onSelectPage: (Int) -> Void
    let onSelectDay: (Int64, Date) -> Void

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
                        onSelectDay: onSelectDay,
                        onSelectPage: onSelectPage,
                        chromeInsets: proxy.safeAreaInsets,
                        headerHeight: headerHeight
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            // On the TabView, not on the GeometryReader: a reader that ignores the safe area
            // reports no insets, which would leave the pages with no padding.
            .ignoresSafeArea(.container, edges: .vertical)
        }
        // Pinned above the pages, so the place name stays legible while a page scrolls under it
        // and the dots keep a fixed position.
        //
        // Attached outside the `GeometryReader`, whose frame ignores the safe area and so gives
        // nothing sensible to align to.
        .overlay(alignment: .top) {
            HomeStickyHeader(state: state, onSelectPage: onSelectPage)
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { headerHeight = $0 }
        }
        // Without this the navigation bar paints its own background across the top of the screen.
        // Hiding it leaves only the toolbar items.
        .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
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
    let onSelectDay: (Int64, Date) -> Void
    let onSelectPage: (Int) -> Void
    /// The insets the pager gave up so content could run behind the bars; see
    /// ``HomeContent/pager``. Re-applied here as content padding.
    let chromeInsets: EdgeInsets
    /// How far down the page's content has to start to clear the pinned header.
    let headerHeight: CGFloat

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
                            showsLocationName: false
                        )

                        HourlyStrip(
                            // Every page draws its own strip, including the ones off-screen. The
                            // slice is cheap, and skipping it made the section appear mid-swipe.
                            hours: state.hourlyWindow(for: page),
                            timeZone: weather.timeZone,
                            unit: state.preferences.temperatureUnit
                        )

                        // The forecast picture.
                        if let forecast = page.forecast.data {
                            TemperatureTrendSection(
                                forecast: forecast,
                                unit: state.preferences.temperatureUnit
                            )
                            DailyRangesSection(
                                forecast: forecast,
                                unit: state.preferences.temperatureUnit,
                                onSelectDay: { date in onSelectDay(weather.locationID, date) }
                            )
                        }

                        SectionHeader("Conditions")

                        if let sun = weather.sunPath() {
                            SunPathCard(
                                progress: sun.progress,
                                sunriseLabel: sun.sunriseLabel,
                                sunsetLabel: sun.sunsetLabel,
                                daylightLabel: sun.daylightLabel,
                                announcement: sun.announcement
                            )
                        }

                        // Absent at latitudes and dates with no such window; see `SolarCalculator`.
                        if let golden = goldenHourReading(
                            for: page.location,
                            timeZone: weather.timeZone
                        ) {
                            GoldenHourCard(reading: golden)
                        }

                        // All eight tiles, including the derived dew point and length of day. Home used
                        // to show six and keep those two behind the hero tap; with nowhere to tap
                        // through to, holding them back would just be hiding them.
                        WeatherDetailGrid(
                            details: weather.details(
                                preferences: state.preferences,
                                includeDerived: true
                            )
                        )

                        ObservedAtFooter(observedAt: weather.observedAt)
                    } else {
                        // A page reached by swiping ahead of its data.
                        LoadingView(message: "Fetching the latest weather…")
                    }
                }
                .padding(.horizontal, Spacing.md)
                // The header sits inside the safe area, so a page has to clear both it and the
                // status bar it passes behind.
                .padding(.top, chromeInsets.top + headerHeight)
                // The tab bar's own inset, plus room to scroll the last tile clear of it.
                .padding(.bottom, chromeInsets.bottom + Self.tabBarClearance)
            }
        }
        // Softens where content meets the navigation bar and the minimised tab bar.
        .scrollEdgeEffectStyle(.soft, for: .all)
        // Native pull-to-refresh; also drives the Retry action above.
        .refreshable { await onRefresh() }
    }

    /// Wide enough for the dots and no wider.
    ///
    /// Each dot occupies roughly 16 points, plus its own end padding.
    private func scrubberWidth(for count: Int) -> CGFloat {
        CGFloat(count) * Self.scrubberDotSpacing
    }

    private static let scrubberDotSpacing: CGFloat = 18
    private static let tabBarClearance: CGFloat = 56

    /// `UIPageControl` sizes itself; this only reserves the row.
    private static let scrubberHeight: CGFloat = 28
}

/// A menu of the saved places, for jumping straight to one.
///
/// Icon-only, and only present when there is more than one place. Shown as a `Picker` so the place
/// on screen carries a tick.
struct LocationMenu: View {
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
            onSelectDay: { _, _ in }
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
            onSelectDay: { _, _ in }
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
            onSelectDay: { _, _ in }
        )
    }
}
