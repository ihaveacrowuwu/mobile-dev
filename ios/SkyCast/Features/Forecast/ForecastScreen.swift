import SwiftUI

/// The Forecast tab, five days for the primary location, tappable through to a day detail.
struct ForecastScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: ForecastViewModel?
    @State private var selectedDay: DayRoute?

    var body: some View {
        Group {
            if let viewModel {
                ForecastContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() },
                    onDismissBanner: viewModel.dismissBanner,
                    onOpenDay: { forecast, day in
                        selectedDay = DayRoute(locationID: forecast.locationID, date: day.date)
                    }
                )
            } else {
                LoadingView()
            }
        }
        .navigationTitle("Forecast")
        .navigationDestination(item: $selectedDay) { route in
            DayDetailScreen(locationID: route.locationID, date: route.date)
        }
        .task {
            if viewModel == nil {
                viewModel = ForecastViewModel(
                    weatherRepository: container.weatherRepository,
                    locationRepository: container.locationRepository,
                    settingsStore: container.settingsStore
                )
                viewModel?.start()
            } else {
                // The primary location may have changed on the Locations tab while this screen
                // was off-screen.
                await viewModel?.reload()
            }
        }
        .onDisappear { viewModel?.stop() }
    }
}

/// A pushed day-detail destination. A dedicated value type rather than a tuple, because
/// `navigationDestination(item:)` needs `Hashable`.
struct DayRoute: Hashable, Identifiable {
    let locationID: Int64
    let date: Date

    var id: String {
        "\(locationID)-\(date.timeIntervalSince1970)"
    }
}

/// The stateless half.
struct ForecastContent: View {
    let state: ForecastUiState
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void
    let onOpenDay: (Forecast, ForecastDay) -> Void

    var body: some View {
        if state.showsFullScreenLoader {
            LoadingView(message: "Fetching the forecast…")
        } else if state.showsEmptyState {
            EmptyStateView(
                title: "No locations yet",
                message: "Add a place on the Locations tab and its five-day forecast appears here.",
                systemImage: "mappin.and.ellipse"
            )
        } else if state.showsFullScreenError, let error = state.error {
            ErrorView(error: error, onRetry: { Task { await onRefresh() } })
        } else if let forecast = state.forecast {
            list(forecast)
        } else {
            // Reachable only if the API returns a forecast with no days at all.
            EmptyStateView(
                title: "No forecast available",
                message: "OpenWeather returned no daily readings for this place.",
                systemImage: "calendar.badge.exclamationmark"
            )
        }
    }

    private func list(_ forecast: Forecast) -> some View {
        List {
            if state.showsStaleBanner {
                StaleDataBanner(
                    message: state.staleBannerMessage,
                    onRetry: { Task { await onRefresh() } },
                    onDismiss: onDismissBanner
                )
                // The banner supplies its own glass padding; a row inset on top of it would
                // read as a second, misaligned pane.
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            Section(forecast.locationName) {
                ForEach(forecast.days) { day in
                    Button {
                        onOpenDay(forecast, day)
                    } label: {
                        ForecastDayRow(
                            day: day,
                            timeZone: forecast.timeZone,
                            unit: state.preferences.temperatureUnit
                        )
                    }
                    // A plain button style inside a List keeps the row looking like a row; the
                    // default styling would tint every label blue.
                    .buttonStyle(.plain)
                }
            }
        }
        .refreshable { await onRefresh() }
        // Subtle here: enough that the tabs feel like one app, not so much that a five-day list
        // competes with the forecast for attention.
        .weatherBackground(
            condition: forecast.days.first?.condition ?? .unknown,
            isDaytime: true,
            intensity: .subtle
        )
    }
}

/// One day of the forecast.
struct ForecastDayRow: View {
    let day: ForecastDay
    /// The forecast location's zone. `day.date` is the start of that day *there*, so labelling it
    /// in the device's zone can name the wrong weekday entirely.
    let timeZone: TimeZone
    let unit: TemperatureUnit

    private var high: Int {
        Int(unit.convertFromCelsius(day.maxTemperatureCelsius).rounded())
    }

    private var low: Int {
        Int(unit.convertFromCelsius(day.minTemperatureCelsius).rounded())
    }

    private var rainPercent: Int {
        Int((day.precipitationProbability * 100).rounded())
    }

    private var dayLabel: String {
        // The zone goes in the initialiser. `.timeZone(_:)` on the style would instead add the
        // zone's *name* to the output, which is a different thing entirely.
        let style = Date.FormatStyle(timeZone: timeZone)
            .weekday(.wide)
            .day()
            .month(.abbreviated)
        return day.date.formatted(style)
    }

    var body: some View {
        HStack(spacing: Spacing.md) {
            ConditionBadge(
                condition: day.condition,
                // A forecast row summarises a whole day, so daytime artwork is the honest choice.
                isDaytime: true,
                size: forecastBadgeSize
            )

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(dayLabel)
                    .font(.headline)
                Text(day.description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: Spacing.sm)

            VStack(alignment: .trailing, spacing: Spacing.xxs) {
                Text("\(high)° / \(low)°")
                    .font(.headline)
                if rainPercent > 0 {
                    Label("\(rainPercent)%", systemImage: "drop.fill")
                        .font(.caption)
                        .foregroundStyle(Color.skyAccent)
                        .labelStyle(.titleAndIcon)
                }
            }
        }
        .padding(.vertical, Spacing.xs)
        // One announcement per row; otherwise VoiceOver reads the date, two bare numbers and a
        // percentage as four disconnected fragments.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
        .accessibilityHint("Shows the hour-by-hour breakdown")
    }

    private var announcement: String {
        var text = "\(dayLabel), \(day.description), high \(high)\(unit.symbol), "
            + "low \(low)\(unit.symbol)"
        if rainPercent > 0 {
            text += ", \(rainPercent) percent chance of rain"
        }
        return text
    }

    /// Smaller than the Home hero badge, 44 pt is also the minimum touch target, so the row
    /// stays tappable at its natural height.
    private let forecastBadgeSize = TouchTarget.minimum
}

// MARK: - Previews

#Preview("Loading") {
    NavigationStack {
        ForecastContent(
            state: ForecastUiState(isLoading: true),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDay: { _, _ in }
        )
        .navigationTitle("Forecast")
    }
}

#Preview("Empty") {
    NavigationStack {
        ForecastContent(
            state: ForecastUiState(hasNoLocation: true),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDay: { _, _ in }
        )
        .navigationTitle("Forecast")
    }
}

#Preview("Offline, no cache") {
    NavigationStack {
        ForecastContent(
            state: ForecastUiState(error: .offline),
            onRefresh: {},
            onDismissBanner: {},
            onOpenDay: { _, _ in }
        )
        .navigationTitle("Forecast")
    }
}
