import Foundation
import Observation
import SwiftUI

/// State for the pushed location-detail screen.
struct LocationDetailUiState: Equatable {
    var location: SavedLocation?
    var weather: Weather?
    var preferences: UserPreferences = .init()
    var isLoading = true
    var isRefreshing = false
    var isStale = false
    var error: AppError?
    /// The id resolved to no saved record: the location was deleted while this screen was open.
    var isMissing = false

    /// No `showsFullScreenLoader` / `showsFullScreenError` counterparts to ``TodayUiState`` here,
    /// deliberately: this screen always has the location's identity to render, so it never shows a
    /// full-screen state over it. Loading and errors for the weather half are inline instead.
    var showsStaleBanner: Bool {
        weather != nil && (error != nil || isStale)
    }

    var staleBannerMessage: String {
        error?.message ?? "Showing saved data, it may be out of date."
    }
}

/// Full conditions for one saved location.
///
/// Takes an **id**, not a `SavedLocation`, so the record is re-resolved on appearance and can
/// report that it is gone.
@MainActor
@Observable
final class LocationDetailViewModel {
    private(set) var state = LocationDetailUiState()

    private let locationID: Int64
    private let weatherRepository: any WeatherRepository
    private let locationRepository: any LocationRepository
    private let settingsStore: SettingsStore

    private var observationTask: Task<Void, Never>?

    init(
        locationID: Int64,
        weatherRepository: any WeatherRepository,
        locationRepository: any LocationRepository,
        settingsStore: SettingsStore
    ) {
        self.locationID = locationID
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
        let error = await weatherRepository.refresh(location)
        state.isRefreshing = false
        state.error = error
    }

    private func observe() async {
        state.preferences = settingsStore.preferences
        do {
            guard let location = try await locationRepository.location(id: locationID) else {
                state.isLoading = false
                state.isMissing = true
                return
            }
            state.location = location
            for await dataState in weatherRepository.currentWeather(for: location) {
                if Task.isCancelled {
                    return
                }
                state.weather = dataState.data ?? state.weather
                state.isLoading = dataState.isLoading
                state.isRefreshing = dataState.isRefreshing
                state.isStale = dataState.isStale
                state.error = dataState.error
            }
        } catch {
            state.isLoading = false
            state.error = AppError.from(error)
        }
    }
}

/// Full conditions for one saved location, pushed from Today or Locations.
struct LocationDetailScreen: View {
    let locationID: Int64

    @Environment(AppContainer.self) private var container
    @State private var viewModel: LocationDetailViewModel?

    var body: some View {
        Group {
            if let viewModel {
                LocationDetailContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() }
                )
            } else {
                LoadingView()
            }
        }
        .navigationTitle(viewModel?.state.location?.name ?? "Location")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel == nil {
                viewModel = LocationDetailViewModel(
                    locationID: locationID,
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
struct LocationDetailContent: View {
    let state: LocationDetailUiState
    let onRefresh: () async -> Void

    var body: some View {
        if state.isMissing {
            EmptyStateView(
                title: "Place no longer saved",
                message: "It looks like this location was removed. Go back to see your list.",
                systemImage: "mappin.slash"
            )
        } else {
            content
        }
    }

    private var content: some View {
        ScrollView {
            SkyGlassGroup(spacing: Spacing.md) {
                VStack(spacing: Spacing.md) {
                    if state.showsStaleBanner {
                        StaleDataBanner(
                            message: state.staleBannerMessage,
                            onRetry: { Task { await onRefresh() } }
                        )
                    }

                    // Identity first, and unconditionally. Which place this is comes from
                    // SwiftData, so it is known before any network call, hiding it behind a
                    // spinner would blank a screen whose most important fact is already in hand.
                    // Same offline-first rule as the Today tab, applied to a pushed screen.
                    if let location = state.location {
                        LocationIdentity(location: location)
                    }

                    if let weather = state.weather {
                        // No onTap: this *is* the detail screen, so there is nowhere further
                        // to push.
                        CurrentConditionsHero(
                            weather: weather,
                            unit: state.preferences.temperatureUnit
                        )

                        WeatherDetailGrid(
                            details: weather.details(preferences: state.preferences)
                        )

                        Text("Observed \(weather.observedAt.formatted(date: .abbreviated, time: .shortened))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                    } else {
                        // Inline, not full-screen: the identity block above is real content, so a
                        // full-screen loader or error over the top of it would be a lie.
                        WeatherStatusNotice(
                            error: state.error,
                            onRetry: { Task { await onRefresh() } }
                        )
                    }
                }
                .padding(Spacing.md)
            }
        }
        // See TodayScreen: the tiles need the grouped background to read against.
        .background(Color.skyBackground)
        .scrollEdgeEffectStyle(.soft, for: .all)
        .refreshable { await onRefresh() }
    }
}

/// Place name and coordinates, both known from the local database.
private struct LocationIdentity: View {
    let location: SavedLocation

    var body: some View {
        VStack(spacing: Spacing.xxs) {
            Text(location.displayName)
                .font(.headline)
                .multilineTextAlignment(.center)
            Text(coordinates)
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        // One announcement: two lines identifying a single place.
        .accessibilityElement(children: .combine)
    }

    private var coordinates: String {
        String(format: "%.4f, %.4f", location.latitude, location.longitude)
    }
}

/// The weather half's loading or error state, rendered **inline** beneath the identity block.
private struct WeatherStatusNotice: View {
    /// `nil` means a fetch is simply still in flight.
    let error: AppError?
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.sm) {
            if let error {
                Text(error.title)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                Text(error.message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                if error.isRetryable {
                    Button("Retry", action: onRetry)
                        .buttonStyle(.glass)
                }
            } else {
                ProgressView()
                Text("Fetching the latest weather…")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.lg)
    }
}
