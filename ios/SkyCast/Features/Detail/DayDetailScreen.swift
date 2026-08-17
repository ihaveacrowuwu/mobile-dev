import Foundation
import Observation
import SwiftUI

/// State for the pushed day-detail screen.
struct DayDetailUiState: Equatable {
    var locationName = ""
    var day: ForecastDay?
    /// The forecast location's zone, so the hourly rows read as that place's clock. UTC only for
    /// the initial, dataless state.
    var timeZone: TimeZone = .gmt
    var preferences: UserPreferences = .init()
    var isLoading = true
    var error: AppError?
    /// The requested day is not in the cached forecast, it has rolled out of the 5-day window.
    var isMissing = false
}

/// The 3-hourly breakdown for one forecast day.
///
/// Resolves the day from the cached forecast rather than taking a `ForecastDay` through the
/// navigation path, for the same reason ``LocationDetailViewModel`` takes an id: a value embedded
/// in the path is a snapshot that silently goes stale.
@MainActor
@Observable
final class DayDetailViewModel {
    private(set) var state = DayDetailUiState()

    private let locationID: Int64
    private let date: Date
    private let weatherRepository: any WeatherRepository
    private let locationRepository: any LocationRepository
    private let settingsStore: SettingsStore

    private var observationTask: Task<Void, Never>?

    init(
        locationID: Int64,
        date: Date,
        weatherRepository: any WeatherRepository,
        locationRepository: any LocationRepository,
        settingsStore: SettingsStore
    ) {
        self.locationID = locationID
        self.date = date
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

    private func observe() async {
        state.preferences = settingsStore.preferences
        do {
            guard let location = try await locationRepository.location(id: locationID) else {
                state.isLoading = false
                state.isMissing = true
                return
            }
            state.locationName = location.name

            for await dataState in weatherRepository.forecast(for: location) {
                if Task.isCancelled {
                    return
                }
                if let forecast = dataState.data {
                    state.locationName = forecast.locationName
                    state.timeZone = forecast.timeZone
                    // Compare by calendar day, not by instant: the date arrived from a row whose
                    // timestamp is the start of that day in the *device's* calendar, and an
                    // equality check on `Date` would fail on any difference at all.
                    // Compared in the forecast location's calendar, matching the zone the
                    // mapper grouped the days in. `Calendar.current` here would disagree with the
                    // grouping whenever the device and the place are in different zones.
                    var calendar = Calendar(identifier: .gregorian)
                    calendar.timeZone = forecast.timeZone
                    let match = forecast.days.first {
                        calendar.isDate($0.date, inSameDayAs: date)
                    }
                    state.day = match ?? state.day
                    state.isMissing = state.day == nil && !dataState.isLoading
                }
                state.isLoading = dataState.isLoading
                // A refresh failure with a day already resolved is not worth a banner here: the
                // hourly readings for a single past-or-present day do not change materially.
                state.error = state.day == nil ? dataState.error : nil
            }
        } catch {
            state.isLoading = false
            state.error = AppError.from(error)
        }
    }
}

/// The 3-hourly breakdown for one forecast day, pushed from the Forecast tab.
struct DayDetailScreen: View {
    let locationID: Int64
    let date: Date

    @Environment(AppContainer.self) private var container
    @State private var viewModel: DayDetailViewModel?

    var body: some View {
        Group {
            if let viewModel {
                DayDetailContent(state: viewModel.state)
            } else {
                LoadingView()
            }
        }
        // The forecast location's zone, matching the row that pushed this screen, otherwise the
        // title can name a different weekday from the row the user tapped.
        .navigationTitle(
            date.formatted(
                Date.FormatStyle(timeZone: viewModel?.state.timeZone ?? .gmt)
                    .weekday(.wide)
                    .day()
                    .month(.abbreviated)
            )
        )
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel == nil {
                viewModel = DayDetailViewModel(
                    locationID: locationID,
                    date: date,
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
struct DayDetailContent: View {
    let state: DayDetailUiState

    var body: some View {
        if state.isMissing {
            EmptyStateView(
                title: "Day not available",
                message: "This day has rolled out of the five-day forecast window.",
                systemImage: "calendar.badge.exclamationmark"
            )
        } else if let day = state.day {
            list(day)
        } else if let error = state.error {
            // No retry: the Forecast tab owns refreshing, and this screen has no location to
            // refresh against until one resolves.
            ErrorView(error: error)
        } else {
            LoadingView()
        }
    }

    private func list(_ day: ForecastDay) -> some View {
        List {
            Section {
                DaySummary(day: day, unit: state.preferences.temperatureUnit)
                    .listRowBackground(Color.clear)
            } header: {
                Text(state.locationName)
            }

            Section("Hour by hour") {
                ForEach(day.hourly) { hour in
                    HourlyRow(
                        hour: hour,
                        timeZone: state.timeZone,
                        temperatureUnit: state.preferences.temperatureUnit,
                        windUnit: state.preferences.windSpeedUnit
                    )
                }
            }
        }
    }
}

/// The day's headline: condition, description and high/low.
private struct DaySummary: View {
    let day: ForecastDay
    let unit: TemperatureUnit

    private var high: Int {
        Int(unit.convertFromCelsius(day.maxTemperatureCelsius).rounded())
    }

    private var low: Int {
        Int(unit.convertFromCelsius(day.minTemperatureCelsius).rounded())
    }

    var body: some View {
        VStack(spacing: Spacing.sm) {
            ConditionBadge(condition: day.condition, isDaytime: true)
            Text(day.description)
                .font(.title3.weight(.semibold))
            Text("High \(high)\(unit.symbol) · Low \(low)\(unit.symbol)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.sm)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "\(day.description), high \(high)\(unit.symbol), low \(low)\(unit.symbol)"
        )
    }
}

/// One 3-hourly reading.
private struct HourlyRow: View {
    let hour: HourlyForecast
    /// The forecast location's zone, not the device's, or a Maldivian forecast read from London
    /// would list its afternoon readings as morning ones.
    let timeZone: TimeZone
    let temperatureUnit: TemperatureUnit
    let windUnit: WindSpeedUnit

    private var time: String {
        hour.time.formatted(
            Date.FormatStyle(date: .omitted, time: .shortened, timeZone: timeZone)
        )
    }

    private var temperature: Int {
        Int(temperatureUnit.convertFromCelsius(hour.temperatureCelsius).rounded())
    }

    private var wind: String {
        let value = windUnit.convertFromMetresPerSecond(hour.windSpeedMetresPerSecond)
        return "\((value * 10).rounded() / 10) \(windUnit.symbol)"
    }

    private var rainPercent: Int {
        Int((hour.precipitationProbability * 100).rounded())
    }

    var body: some View {
        HStack(spacing: Spacing.md) {
            Text(time)
                .font(.subheadline)
                .monospacedDigit()
                // A fixed width keeps the temperature column aligned down the list; without it
                // "9 AM" and "12 PM" push everything sideways.
                .frame(width: timeColumnWidth, alignment: .leading)

            Image(systemName: hour.condition.symbolName(isDaytime: isDaytime))
                .symbolRenderingMode(.multicolor)
                .accessibilityHidden(true)

            Text("\(temperature)\(temperatureUnit.symbol)")
                .font(.headline)
                .monospacedDigit()

            Spacer(minLength: Spacing.sm)

            VStack(alignment: .trailing, spacing: Spacing.xxs) {
                Text(wind)
                    .font(.caption)
                if rainPercent > 0 {
                    Text("\(rainPercent)%")
                        .font(.caption)
                        .foregroundStyle(Color.skyAccent)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
    }

    /// The forecast carries no sunrise/sunset, so daylight is approximated by clock hour. Getting
    /// this wrong would show a sun at 3 am, the exact parity bug `WeatherConditionIconTest`
    /// guards against, so it is worth being explicit rather than passing `isDaytime: true`.
    private var isDaytime: Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let hourOfDay = calendar.component(.hour, from: hour.time)
        return hourOfDay >= dawnHour && hourOfDay < duskHour
    }

    private var announcement: String {
        var text = "\(time), \(temperature)\(temperatureUnit.symbol), wind \(wind)"
        if rainPercent > 0 {
            text += ", \(rainPercent) percent chance of rain"
        }
        return text
    }

    private let timeColumnWidth: CGFloat = 64
    private let dawnHour = 6
    private let duskHour = 20
}
