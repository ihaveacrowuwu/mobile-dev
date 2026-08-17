import Foundation
import Observation

/// Everything the Forecast screen renders.
///
/// Same derived-property approach as ``TodayUiState``: the offline-first display rules live on
/// the state, so the view never combines flags itself.
struct ForecastUiState: Equatable {
    var location: SavedLocation?
    var forecast: Forecast?
    var preferences: UserPreferences = .init()
    var isLoading = false
    var isRefreshing = false
    var isStale = false
    var error: AppError?
    var hasNoLocation = false
    var isBannerDismissed = false

    var showsFullScreenLoader: Bool {
        isLoading && forecast == nil
    }

    var showsFullScreenError: Bool {
        error != nil && forecast == nil && !hasNoLocation
    }

    var showsEmptyState: Bool {
        hasNoLocation && !isLoading
    }

    var showsContent: Bool {
        forecast?.days.isEmpty == false
    }

    var showsStaleBanner: Bool {
        showsContent && !isBannerDismissed && (error != nil || isStale)
    }

    var staleBannerMessage: String {
        error?.message ?? "Showing saved data, it may be out of date."
    }
}

/// View model for the Forecast tab.
///
/// Mirrors ``TodayViewModel`` deliberately, including the single held `observationTask`: starting
/// a second stream without cancelling the first would leave two writers racing on `state`.
@MainActor
@Observable
final class ForecastViewModel {
    private(set) var state = ForecastUiState(isLoading: true)

    private let weatherRepository: any WeatherRepository
    private let locationRepository: any LocationRepository
    private let settingsStore: SettingsStore

    private var observationTask: Task<Void, Never>?

    init(
        weatherRepository: any WeatherRepository,
        locationRepository: any LocationRepository,
        settingsStore: SettingsStore
    ) {
        self.weatherRepository = weatherRepository
        self.locationRepository = locationRepository
        self.settingsStore = settingsStore
    }

    func start() {
        guard observationTask == nil else { return }
        observationTask = Task { await observe() }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
    }

    func refresh() async {
        guard let location = state.location else { return }

        state.isRefreshing = true
        state.isBannerDismissed = false

        let error = await weatherRepository.refresh(location)

        state.isRefreshing = false
        if error == nil {
            await observeForecast(for: location)
        } else {
            state.error = error
        }
    }

    func dismissBanner() {
        state.isBannerDismissed = true
    }

    /// Re-reads the primary location and its forecast.
    ///
    /// Called when the tab reappears, because the user may have changed the primary place on the
    /// Locations tab while this screen was off-screen. Android gets this for free from Room's
    /// `Flow`; SwiftData has no equivalent outside a `View`, so the reload is explicit.
    func reload() async {
        stop()
        state.isLoading = state.forecast == nil
        start()
    }

    // MARK: - Internals

    private func observe() async {
        state.preferences = settingsStore.preferences

        do {
            guard let location = try await locationRepository.primaryLocation() else {
                state.isLoading = false
                state.hasNoLocation = true
                return
            }
            state.location = location
            state.hasNoLocation = false
            await observeForecast(for: location)
        } catch {
            state.isLoading = false
            state.error = AppError.from(error)
        }
    }

    private func observeForecast(for location: SavedLocation) async {
        for await dataState in weatherRepository.forecast(for: location) {
            if Task.isCancelled {
                return
            }
            apply(dataState, location: location)
        }
    }

    private func apply(_ dataState: DataState<Forecast>, location: SavedLocation) {
        state.location = location
        // Keep the previous forecast on failure, this is the visible half of the offline-first
        // promise, and the reason `DataState` carries data and error together.
        state.forecast = dataState.data ?? state.forecast
        state.isLoading = dataState.isLoading
        state.isRefreshing = dataState.isRefreshing
        state.isStale = dataState.isStale
        state.error = dataState.error
        state.preferences = settingsStore.preferences
    }
}
