import Foundation
import Observation

/// View model for the Today screen.
///
/// Note what is **not** here: no SwiftUI, no URLSession, no SwiftData, no `ModelContext`. Its only
/// collaborators are `Domain` protocols, so `TodayViewModelTests` runs on the simulator in
/// milliseconds against hand-written fakes.
///
/// `@Observable` + `@MainActor`: state mutations happen on the main actor and SwiftUI observes the
/// properties directly, which replaces the `ObservableObject`/`@Published` boilerplate entirely.
/// The Android counterpart exposes a `StateFlow`; the shape of the state is the same either way.
///
/// ## Observing every saved place
///
/// Today pages between the user's locations, so it holds a stream per location rather than one for
/// the primary. Loading only the visible page would put a spinner behind every swipe. Each stream
/// is served from cache and honours its TTL, so a handful of places produce a handful of requests
/// per TTL window, not per swipe.
@MainActor
@Observable
final class TodayViewModel {
    private(set) var state = TodayUiState(isLoading: true)

    private let weatherRepository: any WeatherRepository
    private let locationRepository: any LocationRepository
    private let settingsStore: SettingsStore

    /// One observation task per saved location, plus the outer one that watches the list itself.
    /// Held so a new set can cancel the old, otherwise a deleted location would keep writing.
    private var observationTask: Task<Void, Never>?
    private var pageTasks: [Int64: Task<Void, Never>] = [:]

    init(
        weatherRepository: any WeatherRepository,
        locationRepository: any LocationRepository,
        settingsStore: SettingsStore
    ) {
        self.weatherRepository = weatherRepository
        self.locationRepository = locationRepository
        self.settingsStore = settingsStore
    }

    /// Starts (or restarts) observation. Safe to call on every `task`, the guard makes a repeated
    /// call a no-op rather than a duplicated stream.
    func start() {
        guard observationTask == nil else { return }
        observationTask = Task { await loadPages() }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
        for task in pageTasks.values {
            task.cancel()
        }
        pageTasks.removeAll()
    }

    /// Re-reads the saved locations.
    ///
    /// Called when the tab reappears, because the user may have added or removed a place while
    /// this screen was off-screen. Android gets this for free from Room's `Flow`; SwiftData has no
    /// equivalent outside a `View`, so the reload is explicit.
    func reload() async {
        await loadPages()
    }

    /// Called when the user swipes the pager or picks a place from the menu.
    func selectPage(_ index: Int) {
        guard state.pages.indices.contains(index) else { return }
        state.selectedIndex = index
    }

    /// Pull-to-refresh and the Retry button both land here.
    func refresh() async {
        guard let location = state.location else { return }

        state.isRefreshing = true
        // A new attempt makes a dismissed banner relevant again.
        state.isBannerDismissed = false

        let error = await weatherRepository.refresh(location)

        state.isRefreshing = false
        state.refreshError = error
        if error == nil {
            // Re-read so the refreshed values reach the screen.
            await observePage(for: location)
        }
    }

    func dismissBanner() {
        state.isBannerDismissed = true
    }

    // MARK: - Internals

    private func loadPages() async {
        state.preferences = settingsStore.preferences

        do {
            let locations = try await locationRepository.savedLocations()
            guard !locations.isEmpty else {
                stopPageTasks()
                state.pages = []
                state.isLoading = false
                state.hasNoLocation = true
                return
            }

            state.hasNoLocation = false
            // Keep whatever is already loaded for locations that survived, so a reload after
            // adding a place does not blank the pages the user was looking at.
            let existing = Dictionary(uniqueKeysWithValues: state.pages.map { ($0.id, $0) })
            state.pages = locations.map { existing[$0.id] ?? TodayPage(location: $0) }

            // Open on the primary place, but only before the user has chosen for themselves.
            if state.selectedIndex == 0, let primary = locations.firstIndex(where: \.isPrimary) {
                state.selectedIndex = primary
            }
            state.selectedIndex = min(state.selectedIndex, locations.count - 1)

            stopPageTasks()
            for location in locations {
                pageTasks[location.id] = Task { await self.observePage(for: location) }
            }
        } catch {
            state.isLoading = false
            state.refreshError = AppError.from(error)
        }
    }

    private func stopPageTasks() {
        for task in pageTasks.values {
            task.cancel()
        }
        pageTasks.removeAll()
    }

    /// Streams one location's weather and forecast into its page.
    private func observePage(for location: SavedLocation) async {
        async let weather: Void = observeWeather(for: location)
        async let forecast: Void = observeForecast(for: location)
        _ = await (weather, forecast)
    }

    private func observeWeather(for location: SavedLocation) async {
        for await dataState in weatherRepository.currentWeather(for: location) {
            if Task.isCancelled {
                return
            }
            update(location) { page in
                // Keep the previous reading on failure: the visible half of the offline-first
                // promise, and the reason `DataState` carries data and error together.
                page.weather = DataState(
                    data: dataState.data ?? page.weather.data,
                    isLoading: dataState.isLoading,
                    isRefreshing: dataState.isRefreshing,
                    isStale: dataState.isStale,
                    error: dataState.error
                )
            }
        }
    }

    private func observeForecast(for location: SavedLocation) async {
        for await dataState in weatherRepository.forecast(for: location) {
            if Task.isCancelled {
                return
            }
            update(location) { page in
                page.forecast = DataState(
                    data: dataState.data ?? page.forecast.data,
                    isLoading: dataState.isLoading,
                    isRefreshing: dataState.isRefreshing,
                    isStale: dataState.isStale,
                    error: dataState.error
                )
            }
        }
    }

    /// Applies a change to one page, by location rather than by index.
    ///
    /// By id, not position: a stream can deliver while the list is being reordered or trimmed, and
    /// writing to a stale index would put one location's weather under another's name.
    private func update(_ location: SavedLocation, _ change: (inout TodayPage) -> Void) {
        guard let index = state.pages.firstIndex(where: { $0.id == location.id }) else { return }
        change(&state.pages[index])
        state.isLoading = state.pages.contains { $0.weather.isLoading }
        state.isRefreshing = state.pages.contains { $0.weather.isRefreshing }
        state.preferences = settingsStore.preferences
    }
}
