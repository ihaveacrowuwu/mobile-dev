import SwiftUI

/// The Today tab.
///
/// Two views, deliberately:
///
/// - ``TodayScreen`` is the **stateful** entry point. It builds the view model from the
///   environment container and does nothing else.
/// - ``TodayContent`` is **stateless**, state in, closures out. That is what makes it
///   previewable in every state and assertable without a live network or database.
///
/// Follow this split for every screen. It is the exact counterpart of the
/// `TodayScreen` / `TodayContent` pair on Android.
struct TodayScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: TodayViewModel?

    var body: some View {
        Group {
            if let viewModel {
                TodayContent(
                    state: viewModel.state,
                    onRefresh: { await viewModel.refresh() },
                    onDismissBanner: viewModel.dismissBanner
                )
            } else {
                LoadingView()
            }
        }
        .navigationTitle("Today")
        .task {
            // @Environment is not available during init, so the view model is built here.
            if viewModel == nil {
                viewModel = TodayViewModel(
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
struct TodayContent: View {
    let state: TodayUiState
    let onRefresh: () async -> Void
    let onDismissBanner: () -> Void

    var body: some View {
        // Exactly one branch renders. The ordering encodes the offline-first rules from
        // The offline-first read algorithm: content wins over errors whenever a cache exists.
        if state.showsFullScreenLoader {
            LoadingView(message: "Fetching the latest weather…")
        } else if state.showsEmptyState {
            EmptyStateView(
                title: "No locations yet",
                message: "Add a place and SkyCast will keep its forecast ready, even offline.",
                systemImage: "mappin.and.ellipse",
                actionTitle: nil,
                action: nil
            )
        } else if state.showsFullScreenError, let error = state.error {
            ErrorView(error: error, onRetry: { Task { await onRefresh() } })
        } else {
            content
        }
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                if state.showsStaleBanner {
                    StaleDataBanner(
                        message: state.staleBannerMessage,
                        onRetry: { Task { await onRefresh() } },
                        onDismiss: onDismissBanner
                    )
                }

                if let weather = state.weather {
                    CurrentConditionsHeader(
                        weather: weather,
                        unit: state.preferences.temperatureUnit
                    )

                    // TODO(nauhaan): replace with the real detail grid, humidity, wind,
                    //  pressure, visibility, sunrise/sunset. Tracked for the Functionality
                    //  criterion; the plumbing above is already final.
                    Text("Humidity, wind, pressure and sunrise details will appear here.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(Spacing.md)
                        .background(Color.skySurface, in: RoundedRectangle(cornerRadius: Radius.md))
                        .padding(.horizontal, Spacing.md)
                }
            }
            .padding(.vertical, Spacing.md)
        }
        .background(Color.skyBackground)
        // Native pull-to-refresh; also drives the Retry action above.
        .refreshable { await onRefresh() }
    }
}

/// The hero reading: place, condition symbol, and one very large temperature.
private struct CurrentConditionsHeader: View {
    let weather: Weather
    let unit: TemperatureUnit

    private var temperature: Int {
        Int(unit.convertFromCelsius(weather.temperatureCelsius).rounded())
    }

    private var feelsLike: Int {
        Int(unit.convertFromCelsius(weather.feelsLikeCelsius).rounded())
    }

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text(weather.locationName)
                .font(.title2.weight(.semibold))

            Image(systemName: weather.condition.symbolName(isDaytime: weather.isDaytime))
                .font(.system(size: 56))
                .symbolRenderingMode(.multicolor)
                .accessibilityHidden(true)

            HStack(alignment: .top, spacing: 0) {
                Text("\(temperature)")
                    .font(.skyHeroTemperature)
                Text(unit.symbol)
                    .font(.title2)
                    .padding(.top, Spacing.md)
            }

            Text(weather.description)
                .font(.body)
                .foregroundStyle(.secondary)

            Text("Feels like \(feelsLike)\(unit.symbol)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.md)
        // One combined announcement. Without this a VoiceOver user hears "London", "22",
        // "°C", "Clear sky", "Feels like 21°C" as five disconnected fragments.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "\(weather.locationName), \(temperature)\(unit.symbol), "
                + "\(weather.description), feels like \(feelsLike)\(unit.symbol)"
        )
    }
}

// MARK: - Previews
// One per state, so every branch is reviewable without a device.

#Preview("Loading") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(isLoading: true),
            onRefresh: {},
            onDismissBanner: {}
        )
    }
}

#Preview("Empty") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(hasNoLocation: true),
            onRefresh: {},
            onDismissBanner: {}
        )
    }
}

#Preview("Offline, no cache") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(error: .offline),
            onRefresh: {},
            onDismissBanner: {}
        )
    }
}

#Preview("No API key") {
    NavigationStack {
        TodayContent(
            state: TodayUiState(error: .unauthorized),
            onRefresh: {},
            onDismissBanner: {}
        )
    }
}
