import Foundation
import Observation
import SwiftUI

/// State for the pushed location-detail screen.
struct LocationDetailUiState: Equatable {
    var location: SavedLocation?
    var weather: Weather?
    /// The multi-day forecast for the same place.
    ///
    /// Optional and never blocking: the current reading renders the moment it arrives, and the
    /// forecast sections appear when they do.
    var forecast: Forecast?
    var preferences: UserPreferences = .init()
    var isLoading = true
    var isRefreshing = false
    var isStale = false
    var error: AppError?
    /// The id resolved to no saved record: the location was deleted while this screen was open.
    var isMissing = false

    /// The readings from now on. Past hours belong on Home, which is about the day in progress.
    func upcomingHours(now: Date = .now, limit: Int) -> [HourlyForecast] {
        Array((forecast?.days.flatMap(\.hourly) ?? []).filter { $0.time >= now }.prefix(limit))
    }

    /// This screen always has the location's identity to render, so loading and errors for the
    /// weather half are inline rather than full-screen.
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
            // Concurrently, and independently: a forecast that fails must not stop the current
            // reading arriving, and vice versa.
            async let weather: Void = observeWeather(for: location)
            async let forecast: Void = observeForecast(for: location)
            _ = await (weather, forecast)
        } catch {
            state.isLoading = false
            state.error = AppError.from(error)
        }
    }

    private func observeWeather(for location: SavedLocation) async {
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
    }

    private func observeForecast(for location: SavedLocation) async {
        for await dataState in weatherRepository.forecast(for: location) {
            if Task.isCancelled {
                return
            }
            // Only the data. A failed forecast hides its section rather than warning about the
            // whole screen.
            state.forecast = dataState.data ?? state.forecast
        }
    }
}

/// Full conditions for one saved location, pushed from Home or Locations.
struct LocationDetailScreen: View {
    let locationID: Int64

    @Environment(AppContainer.self) private var container
    @State private var viewModel: LocationDetailViewModel?
    /// The pushed day. The stack is driven from a value so the rows stay reusable.
    @State private var selectedDay: Date?

    var body: some View {
        Group {
            if let viewModel {
                LocationDetailContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() },
                    onSelectDay: { selectedDay = $0 }
                )
            } else {
                LoadingView()
            }
        }
        .navigationTitle(viewModel?.state.location?.name ?? "Location")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $selectedDay) { date in
            DayDetailScreen(locationID: locationID, date: date)
        }
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
    var onSelectDay: ((Date) -> Void)?

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

                    // Which place this is comes from SwiftData, so it renders unconditionally,
                    // before any network call.
                    if let location = state.location {
                        LocationIdentity(location: location)
                    }

                    if let weather = state.weather {
                        // No onTap: this *is* the detail screen, so there is nowhere further
                        // to push.
                        CurrentConditionsHero(
                            weather: weather,
                            unit: state.preferences.temperatureUnit,
                            // The identity block above already names the place.
                            showsLocationName: false
                        )

                        ForecastSections(state: state, onSelectDay: onSelectDay)

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

                        WeatherDetailGrid(
                            // Eight tiles: dew point and length of day are derived readings.
                            details: weather.details(preferences: state.preferences, includeDerived: true)
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
        // The weather background supplies the grouped base the tiles read against, plus a hint
        // of the condition.
        .weatherBackground(
            condition: state.weather?.condition ?? .unknown,
            isDaytime: state.weather?.isDaytime ?? true,
            intensity: .subtle
        )
        .scrollEdgeEffectStyle(.soft, for: .all)
        .refreshable { await onRefresh() }
    }
}

/// The hour-by-hour and day-by-day picture, when the forecast has arrived.
///
/// Silent when it has not, since the forecast and the current reading are separate requests.
private struct ForecastSections: View {
    let state: LocationDetailUiState
    var onSelectDay: ((Date) -> Void)?

    /// Enough to fill the strip without turning the top of the screen into a second forecast list.
    private let hoursOnStrip = 8

    var body: some View {
        if let forecast = state.forecast {
            let unit = state.preferences.temperatureUnit
            let hours = state.upcomingHours(limit: hoursOnStrip)
            let points = forecast.trendPoints(unit: unit)
            let days = forecast.dayRanges(unit: unit)

            if !hours.isEmpty {
                // The detail screen shows only what is ahead, so it does not reuse Home's
                // "Through the day".
                HourlyStrip(
                    hours: hours,
                    timeZone: forecast.timeZone,
                    unit: unit,
                    title: "Next hours"
                )
                // The strip pads itself horizontally so it can scroll edge to edge; the rest of
                // this screen is already inset, so that padding has to be taken back off.
                .padding(.horizontal, -Spacing.md)
            }

            if points.count > 1 {
                SectionHeader("Temperature trend")
                TemperatureTrend(points: points, summary: forecast.trendSummary(unit: unit))
            }

            if !days.isEmpty {
                SectionHeader("Next days")
                DailyRangeList(days: days, onDaySelected: onSelectDay.map { action in
                    { day in action(day.date) }
                })
            }
        }
    }
}

/// A section title, styled once so the screen's headings cannot drift apart.
private struct SectionHeader: View {
    let title: String

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
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
