import Foundation
import Observation

/// View model for the Today screen.
///
/// Note what is **not** here: no SwiftUI, no URLSession, no SwiftData, no `ModelContext`.
/// Its only collaborators are `Domain` protocols, so `TodayViewModelTests` runs on the
/// simulator in milliseconds against hand-written fakes.
///
/// `@Observable` + `@MainActor`: state mutations happen on the main actor and SwiftUI
/// observes the properties directly, which replaces the `ObservableObject`/`@Published`
/// boilerplate entirely. The Android counterpart exposes a `StateFlow`; the shape of the
/// state is the same either way.
@MainActor
@Observable
final class TodayViewModel {
    private(set) var state = TodayUiState(isLoading: true)

    private let weatherRepository: any WeatherRepository
    private let locationRepository: any LocationRepository
    private let settingsStore: SettingsStore

    /// The in-flight observation. Held so a new one can cancel the old, otherwise
    /// switching location would leave two streams writing to `state`.
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

    /// Starts (or restarts) observation. Safe to call on every `task`, the guard makes a
    /// repeated call a no-op rather than a duplicated stream.
    func start() {
        guard observationTask == nil else { return }
        observationTask = Task { await observe() }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
    }

    /// Pull-to-refresh and the Retry button both land here.
    func refresh() async {
        guard let location = state.location else { return }

        state.isRefreshing = true
        // A new attempt makes a dismissed banner relevant again.
        state.isBannerDismissed = false

        let error = await weatherRepository.refresh(location)

        state.isRefreshing = false
        if error == nil {
            // Re-read so the refreshed values (and cleared error) reach the screen.
            await observeCurrentLocationWeather(location)
        } else {
            state.error = error
        }
    }

    func dismissBanner() {
        state.isBannerDismissed = true
    }

    // MARK: - Internals

    private func observe() async {
        state.preferences = settingsStore.preferences

        do {
            guard let location = try await locationRepository.primaryLocation() else {
                // No location is an empty state, not an error.
                state.isLoading = false
                state.hasNoLocation = true
                return
            }
            state.location = location
            state.hasNoLocation = false
            await observeCurrentLocationWeather(location)
        } catch {
            state.isLoading = false
            state.error = AppError.from(error)
        }
    }

    private func observeCurrentLocationWeather(_ location: SavedLocation) async {
        for await dataState in weatherRepository.currentWeather(for: location) {
            // Cancellation must stop the loop; without this the stream keeps updating
            // state for a screen the user has left.
            if Task.isCancelled {
                return
            }
            apply(dataState, location: location)
        }
    }

    private func apply(_ dataState: DataState<Weather>, location: SavedLocation) {
        state.location = location
        state.weather = dataState.data ?? state.weather
        state.isLoading = dataState.isLoading
        state.isRefreshing = dataState.isRefreshing
        state.isStale = dataState.isStale
        state.error = dataState.error
        state.preferences = settingsStore.preferences
    }
}
